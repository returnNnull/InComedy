package com.bam.incomedy.server.db

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация ticketing foundation persistence.
 *
 * Репозиторий хранит derived inventory units и hold-состояние отдельно от organizer event
 * snapshot-а, чтобы ticketing transitions не мутировали frozen layout и event-local overrides.
 */
class PostgresTicketingRepository(
    private val dataSource: DataSource,
) : TicketingRepository {
    /** Проверяет, совпадает ли persisted inventory sync marker с текущей organizer revision. */
    override fun isInventorySynchronized(
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ): Boolean {
        dataSource.connection.use { connection ->
            return loadInventorySyncState(
                connection = connection,
                eventId = eventId,
            ) == sourceEventUpdatedAt
        }
    }

    /** Синхронизирует derived inventory только на write path или при stale inventory revision. */
    override fun synchronizeInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        sourceEventUpdatedAt: OffsetDateTime,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueOrders(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                inventory.forEach { blueprint ->
                    upsertInventoryUnit(
                        connection = connection,
                        blueprint = blueprint,
                    )
                }
                upsertInventorySyncState(
                    connection = connection,
                    eventId = eventId,
                    sourceEventUpdatedAt = sourceEventUpdatedAt,
                )
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
            return loadInventory(
                connection = connection,
                eventId = eventId,
            )
        }
    }

    /** Возвращает inventory snapshot события без полного derived upsert-а на каждый read. */
    override fun listInventory(
        eventId: String,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueOrders(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                val inventory = loadInventory(
                    connection = connection,
                    eventId = eventId,
                )
                connection.commit()
                return inventory
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Создает новый hold, если unit существует и сейчас доступна для резерва. */
    override fun createSeatHold(
        eventId: String,
        inventoryRef: String,
        userId: String,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredSeatHold {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueOrders(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                val lockedUnit = loadInventoryUnit(
                    connection = connection,
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                    lockForUpdate = true,
                ) ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                )
                lockedUnit.activeHold?.let { hold ->
                    if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                        expireHold(
                            connection = connection,
                            inventoryUnitId = lockedUnit.id,
                            holdId = hold.id,
                        )
                    }
                }
                val refreshedUnit = loadInventoryUnit(
                    connection = connection,
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                    lockForUpdate = true,
                ) ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                )
                if (refreshedUnit.status != "available") {
                    throw TicketingInventoryConflictPersistenceException(
                        inventoryRef = inventoryRef,
                        currentStatus = refreshedUnit.status,
                    )
                }

                val holdId = UUID.randomUUID().toString()
                connection.prepareStatement(
                    """
                    INSERT INTO seat_holds (
                        id,
                        event_id,
                        inventory_unit_id,
                        user_id,
                        expires_at,
                        status,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'active', NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.setObject(2, UUID.fromString(eventId))
                    statement.setObject(3, UUID.fromString(refreshedUnit.id))
                    statement.setObject(4, UUID.fromString(userId))
                    statement.setObject(5, expiresAt)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        active_hold_id = ?,
                        status = 'held',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.setObject(2, UUID.fromString(refreshedUnit.id))
                    statement.executeUpdate()
                }
                val hold = loadSeatHold(
                    connection = connection,
                    holdId = holdId,
                ) ?: error("Persisted hold must be readable right after insert")
                connection.commit()
                return hold
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Создает checkout order, потребляя активные hold-ы и переводя inventory в pending payment.
     *
     * Локирование идет через inventory rows, чтобы checkout creation не расходился по порядку
     * блокировок с create/release/expiry flows hold-ов.
     */
    override fun createTicketOrder(
        eventId: String,
        holdIds: List<String>,
        userId: String,
        checkoutExpiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredTicketOrder {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueOrders(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                val checkoutRows = loadCheckoutHoldRows(
                    connection = connection,
                    holdIds = holdIds,
                )
                val missingHoldIds = holdIds.toSet() - checkoutRows.map(CheckoutLockedHoldRow::holdId).toSet()
                if (missingHoldIds.isNotEmpty()) {
                    throw TicketingSeatHoldNotFoundPersistenceException(missingHoldIds.first())
                }

                val orderId = UUID.randomUUID().toString()
                var orderCurrency: String? = null
                val orderLines = mutableListOf<StoredTicketOrderLine>()
                checkoutRows.forEach { row ->
                    if (row.userId != userId) {
                        throw TicketingCheckoutHoldPermissionDeniedPersistenceException(
                            holdId = row.holdId,
                            userId = userId,
                        )
                    }
                    if (row.eventId != eventId) {
                        throw TicketingCheckoutHoldEventMismatchPersistenceException(
                            holdId = row.holdId,
                            holdEventId = row.eventId,
                            requestedEventId = eventId,
                        )
                    }
                    if (row.holdStatus != "active") {
                        throw TicketingCheckoutConflictPersistenceException(
                            holdId = row.holdId,
                            reasonCode = "hold_inactive",
                        )
                    }
                    if (!row.holdExpiresAt.isAfter(now)) {
                        expireHold(
                            connection = connection,
                            inventoryUnitId = row.inventoryUnitId,
                            holdId = row.holdId,
                        )
                        throw TicketingCheckoutConflictPersistenceException(
                            holdId = row.holdId,
                            reasonCode = "hold_expired",
                        )
                    }
                    if (row.inventoryStatus != "held" || row.activeHoldId != row.holdId) {
                        throw TicketingCheckoutConflictPersistenceException(
                            holdId = row.holdId,
                            reasonCode = "inventory_not_held",
                        )
                    }
                    val priceMinor = row.priceMinor
                        ?: throw TicketingCheckoutPriceMissingPersistenceException(row.inventoryRef)
                    val currentCurrency = row.currency
                    val expectedCurrency = orderCurrency
                    if (expectedCurrency == null) {
                        orderCurrency = currentCurrency
                    } else if (expectedCurrency != currentCurrency) {
                        throw TicketingCheckoutCurrencyMismatchPersistenceException(
                            holdId = row.holdId,
                            expectedCurrency = expectedCurrency,
                            actualCurrency = currentCurrency,
                        )
                    }
                    orderLines += StoredTicketOrderLine(
                        orderId = orderId,
                        inventoryUnitId = row.inventoryUnitId,
                        inventoryRef = row.inventoryRef,
                        label = row.label,
                        priceMinor = priceMinor,
                        currency = currentCurrency,
                    )
                }

                val currency = orderCurrency ?: error("Checkout order currency must be resolved from at least one hold")
                val totalMinor = orderLines.sumOf(StoredTicketOrderLine::priceMinor)
                connection.prepareStatement(
                    """
                    INSERT INTO ticket_orders (
                        id,
                        event_id,
                        user_id,
                        status,
                        currency,
                        total_minor,
                        checkout_expires_at,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, 'awaiting_payment', ?, ?, ?, NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(orderId))
                    statement.setObject(2, UUID.fromString(eventId))
                    statement.setObject(3, UUID.fromString(userId))
                    statement.setString(4, currency)
                    statement.setInt(5, totalMinor)
                    statement.setObject(6, checkoutExpiresAt)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO ticket_order_lines (
                        order_id,
                        inventory_unit_id,
                        inventory_ref,
                        label,
                        price_minor,
                        currency,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NOW())
                    """.trimIndent(),
                ).use { statement ->
                    orderLines.forEach { line ->
                        statement.setObject(1, UUID.fromString(line.orderId))
                        statement.setObject(2, UUID.fromString(line.inventoryUnitId))
                        statement.setString(3, line.inventoryRef)
                        statement.setString(4, line.label)
                        statement.setInt(5, line.priceMinor)
                        statement.setString(6, line.currency)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(
                    """
                    UPDATE seat_holds
                    SET
                        status = 'consumed',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    checkoutRows.forEach { row ->
                        statement.setObject(1, UUID.fromString(row.holdId))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        active_hold_id = NULL,
                        status = 'pending_payment',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    checkoutRows.forEach { row ->
                        statement.setObject(1, UUID.fromString(row.inventoryUnitId))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                val order = loadTicketOrder(
                    connection = connection,
                    orderId = orderId,
                ) ?: error("Persisted ticket order must be readable right after insert")
                connection.commit()
                return order
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Загружает order и при необходимости сначала expiring просроченный pending checkout. */
    override fun findTicketOrder(
        orderId: String,
        now: OffsetDateTime,
    ): StoredTicketOrder? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                var order = loadTicketOrder(
                    connection = connection,
                    orderId = orderId,
                )
                if (order != null &&
                    order.status == "awaiting_payment" &&
                    !order.checkoutExpiresAt.isAfter(now)
                ) {
                    expireOverdueOrders(
                        connection = connection,
                        eventId = order.eventId,
                        now = now,
                    )
                    order = loadTicketOrder(
                        connection = connection,
                        orderId = orderId,
                    )
                }
                connection.commit()
                return order
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Возвращает билеты пользователя, при необходимости довыпуская их для старых `paid` заказов. */
    override fun listIssuedTickets(
        userId: String,
        now: OffsetDateTime,
    ): List<StoredIssuedTicket> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                ensurePaidTicketsIssuedForUser(
                    connection = connection,
                    userId = userId,
                    now = now,
                )
                val tickets = loadIssuedTicketsForUser(
                    connection = connection,
                    userId = userId,
                )
                connection.commit()
                return tickets
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Ищет билет по непрозрачному QR payload. */
    override fun findIssuedTicketByQrPayload(
        qrPayload: String,
        now: OffsetDateTime,
    ): StoredIssuedTicket? {
        dataSource.connection.use { connection ->
            return loadIssuedTicketByQrPayload(
                connection = connection,
                qrPayload = qrPayload,
            )
        }
    }

    /** Возвращает уже созданный checkout session для order-а, если он существует. */
    override fun findTicketCheckoutSession(
        orderId: String,
    ): StoredTicketCheckoutSession? {
        dataSource.connection.use { connection ->
            return loadTicketCheckoutSession(
                connection = connection,
                orderId = orderId,
            )
        }
    }

    /** Возвращает локальный checkout state по `provider_payment_id` для webhook/status verification. */
    override fun findTicketCheckoutStateByProviderPaymentId(
        provider: String,
        providerPaymentId: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        dataSource.connection.use { connection ->
            return loadTicketCheckoutStateByProviderPaymentId(
                connection = connection,
                provider = provider,
                providerPaymentId = providerPaymentId,
            )
        }
    }

    /** Создает checkout session idempotently по `order_id`, чтобы повторный start не плодил записи. */
    override fun createTicketCheckoutSession(
        orderId: String,
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        confirmationUrl: String,
        returnUrl: String,
        checkoutExpiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredTicketCheckoutSession {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val order = loadTicketOrder(
                    connection = connection,
                    orderId = orderId,
                )
                if (order != null &&
                    order.status == "awaiting_payment" &&
                    !order.checkoutExpiresAt.isAfter(now)
                ) {
                    expireOverdueOrders(
                        connection = connection,
                        eventId = order.eventId,
                        now = now,
                    )
                }
                loadTicketCheckoutSession(
                    connection = connection,
                    orderId = orderId,
                )?.let { existing ->
                    connection.commit()
                    return existing
                }
                val sessionId = UUID.randomUUID().toString()
                connection.prepareStatement(
                    """
                    INSERT INTO ticket_checkout_sessions (
                        id,
                        order_id,
                        provider,
                        status,
                        provider_payment_id,
                        provider_status,
                        confirmation_url,
                        return_url,
                        checkout_expires_at,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, 'pending_redirect', ?, ?, ?, ?, ?, NOW(), NOW())
                    ON CONFLICT (order_id) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(sessionId))
                    statement.setObject(2, UUID.fromString(orderId))
                    statement.setString(3, provider)
                    statement.setString(4, providerPaymentId)
                    statement.setString(5, providerStatus)
                    statement.setString(6, confirmationUrl)
                    statement.setString(7, returnUrl)
                    statement.setObject(8, checkoutExpiresAt)
                    statement.executeUpdate()
                }
                val session = loadTicketCheckoutSession(
                    connection = connection,
                    orderId = orderId,
                ) ?: error("Persisted checkout session must be readable right after insert")
                connection.commit()
                return session
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Переводит checkout session в `waiting_for_capture`, если local order еще ожидает оплату. */
    override fun markTicketCheckoutWaitingForCapture(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val state = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                ) ?: run {
                    connection.commit()
                    return null
                }
                val targetStatus = when {
                    state.order.status == "awaiting_payment" &&
                        state.session.status != "succeeded" &&
                        state.session.status != "canceled" -> "waiting_for_capture"
                    else -> state.session.status
                }
                updateTicketCheckoutSessionState(
                    connection = connection,
                    sessionId = state.session.id,
                    status = targetStatus,
                    providerStatus = providerStatus,
                )
                val updatedState = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                )
                connection.commit()
                return updatedState
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Подтверждает финально успешный платеж и переводит inventory unit-ы в `sold`. */
    override fun markTicketCheckoutSucceeded(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val state = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                ) ?: run {
                    connection.commit()
                    return null
                }
                val lockedInventory = loadTicketOrderInventoryRows(
                    connection = connection,
                    orderId = state.order.id,
                    lockForUpdate = true,
                )
                when (state.order.status) {
                    "paid" -> Unit
                    "canceled" -> throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                        orderId = state.order.id,
                        reasonCode = "order_canceled",
                    )

                    "awaiting_payment" -> {
                        if (lockedInventory.any { it.status in setOf("held", "sold") }) {
                            throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                                orderId = state.order.id,
                                reasonCode = "inventory_reassigned",
                            )
                        }
                    }

                    "expired" -> {
                        if (lockedInventory.any { it.status in setOf("held", "pending_payment", "sold") }) {
                            throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                                orderId = state.order.id,
                                reasonCode = "inventory_reassigned",
                            )
                        }
                    }

                    else -> throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                        orderId = state.order.id,
                        reasonCode = "order_state_invalid",
                    )
                }
                updateTicketOrderStatus(
                    connection = connection,
                    orderId = state.order.id,
                    status = "paid",
                )
                updateTicketCheckoutSessionState(
                    connection = connection,
                    sessionId = state.session.id,
                    status = "succeeded",
                    providerStatus = providerStatus,
                )
                markInventorySold(
                    connection = connection,
                    inventoryUnitIds = lockedInventory.map(LockedOrderInventoryRow::inventoryUnitId),
                )
                issueTicketsForOrder(
                    connection = connection,
                    orderId = state.order.id,
                    now = now,
                )
                val updatedState = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                )
                connection.commit()
                return updatedState
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Идемпотентно отмечает билет использованным на входе. */
    override fun markIssuedTicketCheckedIn(
        ticketId: String,
        checkedInByUserId: String,
        now: OffsetDateTime,
    ): StoredTicketCheckInResult? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val ticket = loadIssuedTicketById(
                    connection = connection,
                    ticketId = ticketId,
                    lockForUpdate = true,
                ) ?: run {
                    connection.commit()
                    return null
                }
                if (ticket.status == "issued") {
                    connection.prepareStatement(
                        """
                        UPDATE tickets
                        SET
                            status = 'checked_in',
                            checked_in_at = ?,
                            checked_in_by_user_id = ?,
                            updated_at = NOW()
                        WHERE id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setObject(1, now)
                        statement.setObject(2, UUID.fromString(checkedInByUserId))
                        statement.setObject(3, UUID.fromString(ticketId))
                        statement.executeUpdate()
                    }
                    val updatedTicket = loadIssuedTicketById(
                        connection = connection,
                        ticketId = ticketId,
                        lockForUpdate = false,
                    ) ?: error("Checked-in ticket must remain readable")
                    connection.commit()
                    return StoredTicketCheckInResult(
                        resultCode = "checked_in",
                        ticket = updatedTicket,
                    )
                }
                connection.commit()
                return StoredTicketCheckInResult(
                    resultCode = "duplicate",
                    ticket = ticket,
                )
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Фиксирует отмененный платеж и освобождает inventory, если платеж еще не был подтвержден. */
    override fun markTicketCheckoutCanceled(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val state = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                ) ?: run {
                    connection.commit()
                    return null
                }
                val lockedInventory = loadTicketOrderInventoryRows(
                    connection = connection,
                    orderId = state.order.id,
                    lockForUpdate = true,
                )
                if (state.order.status != "paid") {
                    updateTicketOrderStatus(
                        connection = connection,
                        orderId = state.order.id,
                        status = "canceled",
                    )
                    releasePendingPaymentInventory(
                        connection = connection,
                        inventoryUnitIds = lockedInventory.map(LockedOrderInventoryRow::inventoryUnitId),
                    )
                }
                updateTicketCheckoutSessionState(
                    connection = connection,
                    sessionId = state.session.id,
                    status = if (state.order.status == "paid") "succeeded" else "canceled",
                    providerStatus = providerStatus,
                )
                val updatedState = loadTicketCheckoutStateByProviderPaymentId(
                    connection = connection,
                    provider = provider,
                    providerPaymentId = providerPaymentId,
                )
                connection.commit()
                return updatedState
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Освобождает hold и восстанавливает inventory unit к ее базовой доступности.
     *
     * Лок берется сначала на inventory row, чтобы create/release/expiry flows всегда шли в одном
     * порядке и не образовывали deadlock через пересечение `inventory -> hold` и `hold -> inventory`.
     */
    override fun releaseSeatHold(
        holdId: String,
        userId: String,
        now: OffsetDateTime,
    ): StoredSeatHold {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            var committed = false
            try {
                val lockedHold = loadSeatHoldLockedByInventory(
                    connection = connection,
                    holdId = holdId,
                ) ?: throw TicketingSeatHoldNotFoundPersistenceException(holdId)
                if (lockedHold.userId != userId) {
                    throw TicketingSeatHoldPermissionDeniedPersistenceException(
                        holdId = holdId,
                        userId = userId,
                    )
                }
                if (lockedHold.status == "active" && !lockedHold.expiresAt.isAfter(now)) {
                    expireHold(
                        connection = connection,
                        inventoryUnitId = lockedHold.inventoryUnitId,
                        holdId = lockedHold.id,
                    )
                    connection.commit()
                    committed = true
                    throw TicketingSeatHoldInactivePersistenceException(
                        holdId = holdId,
                        currentStatus = "expired",
                    )
                }
                if (lockedHold.status != "active") {
                    throw TicketingSeatHoldInactivePersistenceException(
                        holdId = holdId,
                        currentStatus = lockedHold.status,
                    )
                }
                connection.prepareStatement(
                    """
                    UPDATE seat_holds
                    SET
                        status = 'released',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        active_hold_id = NULL,
                        status = base_status,
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(lockedHold.inventoryUnitId))
                    statement.executeUpdate()
                }
                val releasedHold = loadSeatHold(
                    connection = connection,
                    holdId = holdId,
                ) ?: error("Released hold must remain readable")
                connection.commit()
                committed = true
                return releasedHold
            } catch (error: Throwable) {
                if (!committed) {
                    connection.rollback()
                }
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Истекает просроченные hold-ы события и восстанавливает их inventory units. */
    private fun expireOverdueHolds(
        connection: Connection,
        eventId: String,
        now: OffsetDateTime,
    ) {
        val expiredPairs = connection.prepareStatement(
            """
            SELECT
                i.id AS inventory_unit_id,
                h.id AS hold_id
            FROM ticket_inventory_units i
            JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
              AND h.status = 'active'
              AND h.expires_at <= ?
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, now)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            result.getObject("inventory_unit_id").toString() to
                                result.getObject("hold_id").toString(),
                        )
                    }
                }
            }
        }
        expiredPairs.forEach { (inventoryUnitId, holdId) ->
            expireHold(
                connection = connection,
                inventoryUnitId = inventoryUnitId,
                holdId = holdId,
            )
        }
    }

    /** Истекает pending checkout order-ы события и возвращает их inventory unit-ы в base status. */
    private fun expireOverdueOrders(
        connection: Connection,
        eventId: String,
        now: OffsetDateTime,
    ) {
        val expiredRows = connection.prepareStatement(
            """
            SELECT
                o.id AS order_id,
                l.inventory_unit_id
            FROM ticket_orders o
            JOIN ticket_order_lines l
              ON l.order_id = o.id
            JOIN ticket_inventory_units i
              ON i.id = l.inventory_unit_id
            WHERE o.event_id = ?
              AND o.status = 'awaiting_payment'
              AND o.checkout_expires_at <= ?
            ORDER BY o.id, l.inventory_unit_id
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, now)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            result.getObject("order_id").toString() to
                                result.getObject("inventory_unit_id").toString(),
                        )
                    }
                }
            }
        }
        if (expiredRows.isEmpty()) {
            return
        }
        expiredRows
            .groupBy(
                keySelector = Pair<String, String>::first,
                valueTransform = Pair<String, String>::second,
            )
            .forEach { (orderId, inventoryUnitIds) ->
                connection.prepareStatement(
                    """
                    UPDATE ticket_orders
                    SET
                        status = 'expired',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(orderId))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_checkout_sessions
                    SET
                        status = 'expired',
                        updated_at = NOW()
                    WHERE order_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(orderId))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        status = base_status,
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    inventoryUnitIds.forEach { inventoryUnitId ->
                        statement.setObject(1, UUID.fromString(inventoryUnitId))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
    }

    /** Считывает persisted sync marker derived inventory конкретного события. */
    private fun loadInventorySyncState(
        connection: Connection,
        eventId: String,
    ): OffsetDateTime? {
        return connection.prepareStatement(
            """
            SELECT source_event_updated_at
            FROM ticket_inventory_sync_state
            WHERE event_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.getObject("source_event_updated_at", OffsetDateTime::class.java)
            }
        }
    }

    /** Обновляет или создает persisted sync marker для derived inventory события. */
    private fun upsertInventorySyncState(
        connection: Connection,
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ) {
        val updatedRows = connection.prepareStatement(
            """
            UPDATE ticket_inventory_sync_state
            SET
                source_event_updated_at = ?,
                reconciled_at = NOW()
            WHERE event_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, sourceEventUpdatedAt)
            statement.setObject(2, UUID.fromString(eventId))
            statement.executeUpdate()
        }
        if (updatedRows > 0) {
            return
        }
        connection.prepareStatement(
            """
            INSERT INTO ticket_inventory_sync_state (
                event_id,
                source_event_updated_at,
                reconciled_at
            ) VALUES (?, ?, NOW())
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, sourceEventUpdatedAt)
            statement.executeUpdate()
        }
    }

    /** Вставляет новую inventory unit или обновляет derived поля уже существующей записи. */
    private fun upsertInventoryUnit(
        connection: Connection,
        blueprint: StoredInventoryUnitBlueprint,
    ) {
        val existing = loadInventoryUnit(
            connection = connection,
            eventId = blueprint.eventId,
            inventoryRef = blueprint.inventoryRef,
            lockForUpdate = true,
        )
        if (existing == null) {
            connection.prepareStatement(
                """
                INSERT INTO ticket_inventory_units (
                    id,
                    event_id,
                    inventory_ref,
                    inventory_type,
                    snapshot_target_type,
                    snapshot_target_ref,
                    label,
                    price_zone_id,
                    price_zone_name,
                    price_minor,
                    currency,
                    base_status,
                    status,
                    active_hold_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NOW(), NOW())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, UUID.fromString(blueprint.eventId))
                statement.setString(3, blueprint.inventoryRef)
                statement.setString(4, blueprint.inventoryType)
                statement.setString(5, blueprint.snapshotTargetType)
                statement.setString(6, blueprint.snapshotTargetRef)
                statement.setString(7, blueprint.label)
                statement.setString(8, blueprint.priceZoneId)
                statement.setString(9, blueprint.priceZoneName)
                statement.setObject(10, blueprint.priceMinor)
                statement.setString(11, blueprint.currency)
                statement.setString(12, blueprint.baseStatus)
                statement.setString(13, blueprint.baseStatus)
                statement.executeUpdate()
            }
            return
        }

        val nextStatus = when {
            existing.activeHold != null -> "held"
            existing.status == "pending_payment" -> "pending_payment"
            existing.status == "sold" -> "sold"
            else -> blueprint.baseStatus
        }
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                inventory_type = ?,
                snapshot_target_type = ?,
                snapshot_target_ref = ?,
                label = ?,
                price_zone_id = ?,
                price_zone_name = ?,
                price_minor = ?,
                currency = ?,
                base_status = ?,
                status = ?,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, blueprint.inventoryType)
            statement.setString(2, blueprint.snapshotTargetType)
            statement.setString(3, blueprint.snapshotTargetRef)
            statement.setString(4, blueprint.label)
            statement.setString(5, blueprint.priceZoneId)
            statement.setString(6, blueprint.priceZoneName)
            statement.setObject(7, blueprint.priceMinor)
            statement.setString(8, blueprint.currency)
            statement.setString(9, blueprint.baseStatus)
            statement.setString(10, nextStatus)
            statement.setObject(11, UUID.fromString(existing.id))
            statement.executeUpdate()
        }
    }

    /** Загружает checkout order со всеми его позициями. */
    private fun loadTicketOrder(
        connection: Connection,
        orderId: String,
    ): StoredTicketOrder? {
        return connection.prepareStatement(
            """
            SELECT
                id,
                event_id,
                user_id,
                status,
                currency,
                total_minor,
                checkout_expires_at
            FROM ticket_orders
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(orderId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredTicketOrder(
                    id = result.getObject("id").toString(),
                    eventId = result.getObject("event_id").toString(),
                    userId = result.getObject("user_id").toString(),
                    status = result.getString("status"),
                    currency = result.getString("currency"),
                    totalMinor = result.getInt("total_minor"),
                    checkoutExpiresAt = result.getObject("checkout_expires_at", OffsetDateTime::class.java),
                    lines = loadTicketOrderLines(connection, orderId),
                )
            }
        }
    }

    /** Загружает зафиксированные позиции checkout order-а. */
    private fun loadTicketOrderLines(
        connection: Connection,
        orderId: String,
    ): List<StoredTicketOrderLine> {
        return connection.prepareStatement(
            """
            SELECT
                order_id,
                inventory_unit_id,
                inventory_ref,
                label,
                price_minor,
                currency
            FROM ticket_order_lines
            WHERE order_id = ?
            ORDER BY inventory_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(orderId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredTicketOrderLine(
                                orderId = result.getObject("order_id").toString(),
                                inventoryUnitId = result.getObject("inventory_unit_id").toString(),
                                inventoryRef = result.getString("inventory_ref"),
                                label = result.getString("label"),
                                priceMinor = result.getInt("price_minor"),
                                currency = result.getString("currency"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Загружает все билеты текущего пользователя в порядке свежести выпуска. */
    private fun loadIssuedTicketsForUser(
        connection: Connection,
        userId: String,
    ): List<StoredIssuedTicket> {
        return connection.prepareStatement(
            """
            SELECT
                t.id,
                t.order_id,
                t.event_id,
                t.inventory_unit_id,
                t.inventory_ref,
                t.label,
                t.status,
                t.qr_payload,
                t.issued_at,
                t.checked_in_at,
                t.checked_in_by_user_id
            FROM tickets t
            JOIN ticket_orders o
              ON o.id = t.order_id
            WHERE o.user_id = ?
            ORDER BY t.issued_at DESC, t.inventory_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.toStoredIssuedTicket())
                    }
                }
            }
        }
    }

    /** Загружает билет по id и при необходимости блокирует строку для check-in мутации. */
    private fun loadIssuedTicketById(
        connection: Connection,
        ticketId: String,
        lockForUpdate: Boolean,
    ): StoredIssuedTicket? {
        val lockClause = if (lockForUpdate) " FOR UPDATE OF t" else ""
        return connection.prepareStatement(
            """
            SELECT
                t.id,
                t.order_id,
                t.event_id,
                t.inventory_unit_id,
                t.inventory_ref,
                t.label,
                t.status,
                t.qr_payload,
                t.issued_at,
                t.checked_in_at,
                t.checked_in_by_user_id
            FROM tickets t
            WHERE t.id = ?$lockClause
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(ticketId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredIssuedTicket()
            }
        }
    }

    /** Загружает билет по полному QR payload. */
    private fun loadIssuedTicketByQrPayload(
        connection: Connection,
        qrPayload: String,
    ): StoredIssuedTicket? {
        return connection.prepareStatement(
            """
            SELECT
                t.id,
                t.order_id,
                t.event_id,
                t.inventory_unit_id,
                t.inventory_ref,
                t.label,
                t.status,
                t.qr_payload,
                t.issued_at,
                t.checked_in_at,
                t.checked_in_by_user_id
            FROM tickets t
            WHERE t.qr_payload = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, qrPayload)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredIssuedTicket()
            }
        }
    }

    /** Загружает checkout session для конкретного order-а. */
    private fun loadTicketCheckoutSession(
        connection: Connection,
        orderId: String,
    ): StoredTicketCheckoutSession? {
        return connection.prepareStatement(
            """
            SELECT
                id,
                order_id,
                provider,
                status,
                provider_payment_id,
                provider_status,
                confirmation_url,
                return_url,
                checkout_expires_at
            FROM ticket_checkout_sessions
            WHERE order_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(orderId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredTicketCheckoutSession(
                    id = result.getObject("id").toString(),
                    orderId = result.getObject("order_id").toString(),
                    provider = result.getString("provider"),
                    status = result.getString("status"),
                    providerPaymentId = result.getString("provider_payment_id"),
                    providerStatus = result.getString("provider_status"),
                    confirmationUrl = result.getString("confirmation_url"),
                    returnUrl = result.getString("return_url"),
                    checkoutExpiresAt = result.getObject("checkout_expires_at", OffsetDateTime::class.java),
                )
            }
        }
    }

    /** Загружает checkout session по внешнему `provider_payment_id`. */
    private fun loadTicketCheckoutSessionByProviderPaymentId(
        connection: Connection,
        provider: String,
        providerPaymentId: String,
    ): StoredTicketCheckoutSession? {
        return connection.prepareStatement(
            """
            SELECT
                id,
                order_id,
                provider,
                status,
                provider_payment_id,
                provider_status,
                confirmation_url,
                return_url,
                checkout_expires_at
            FROM ticket_checkout_sessions
            WHERE provider = ?
              AND provider_payment_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, provider)
            statement.setString(2, providerPaymentId)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredTicketCheckoutSession(
                    id = result.getObject("id").toString(),
                    orderId = result.getObject("order_id").toString(),
                    provider = result.getString("provider"),
                    status = result.getString("status"),
                    providerPaymentId = result.getString("provider_payment_id"),
                    providerStatus = result.getString("provider_status"),
                    confirmationUrl = result.getString("confirmation_url"),
                    returnUrl = result.getString("return_url"),
                    checkoutExpiresAt = result.getObject("checkout_expires_at", OffsetDateTime::class.java),
                )
            }
        }
    }

    /** Собирает объединенное checkout state по внешнему `provider_payment_id`. */
    private fun loadTicketCheckoutStateByProviderPaymentId(
        connection: Connection,
        provider: String,
        providerPaymentId: String,
    ): StoredTicketCheckoutState? {
        val session = loadTicketCheckoutSessionByProviderPaymentId(
            connection = connection,
            provider = provider,
            providerPaymentId = providerPaymentId,
        ) ?: return null
        val order = loadTicketOrder(
            connection = connection,
            orderId = session.orderId,
        ) ?: return null
        return StoredTicketCheckoutState(
            order = order,
            session = session,
        )
    }

    /** Загружает текущие inventory row-ы order-а и при необходимости блокирует их для мутации. */
    private fun loadTicketOrderInventoryRows(
        connection: Connection,
        orderId: String,
        lockForUpdate: Boolean,
    ): List<LockedOrderInventoryRow> {
        val lockClause = if (lockForUpdate) " FOR UPDATE OF i" else ""
        return connection.prepareStatement(
            """
            SELECT
                i.id,
                i.inventory_ref,
                i.status,
                i.base_status
            FROM ticket_order_lines l
            JOIN ticket_inventory_units i
              ON i.id = l.inventory_unit_id
            WHERE l.order_id = ?
            ORDER BY i.id$lockClause
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(orderId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            LockedOrderInventoryRow(
                                inventoryUnitId = result.getObject("id").toString(),
                                inventoryRef = result.getString("inventory_ref"),
                                status = result.getString("status"),
                                baseStatus = result.getString("base_status"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Обновляет статус checkout order-а и его `updated_at`. */
    private fun updateTicketOrderStatus(
        connection: Connection,
        orderId: String,
        status: String,
    ) {
        connection.prepareStatement(
            """
            UPDATE ticket_orders
            SET
                status = ?,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, status)
            statement.setObject(2, UUID.fromString(orderId))
            statement.executeUpdate()
        }
    }

    /** Обновляет статус checkout session и одновременно фиксирует последний provider status. */
    private fun updateTicketCheckoutSessionState(
        connection: Connection,
        sessionId: String,
        status: String,
        providerStatus: String,
    ) {
        connection.prepareStatement(
            """
            UPDATE ticket_checkout_sessions
            SET
                status = ?,
                provider_status = ?,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, status)
            statement.setString(2, providerStatus)
            statement.setObject(3, UUID.fromString(sessionId))
            statement.executeUpdate()
        }
    }

    /** Переводит все inventory unit-ы заказа в финальное `sold` состояние. */
    private fun markInventorySold(
        connection: Connection,
        inventoryUnitIds: List<String>,
    ) {
        if (inventoryUnitIds.isEmpty()) return
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                active_hold_id = NULL,
                status = 'sold',
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            inventoryUnitIds.forEach { inventoryUnitId ->
                statement.setObject(1, UUID.fromString(inventoryUnitId))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Идемпотентно выпускает билеты для оплаченного заказа без дублей по одной inventory unit. */
    private fun issueTicketsForOrder(
        connection: Connection,
        orderId: String,
        now: OffsetDateTime,
    ) {
        val order = loadTicketOrder(
            connection = connection,
            orderId = orderId,
        ) ?: return
        if (order.status != "paid") return
        connection.prepareStatement(
            """
            INSERT INTO tickets (
                id,
                order_id,
                event_id,
                inventory_unit_id,
                inventory_ref,
                label,
                status,
                qr_payload,
                issued_at,
                checked_in_at,
                checked_in_by_user_id,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'issued', ?, ?, NULL, NULL, NOW(), NOW())
            ON CONFLICT (order_id, inventory_unit_id) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            order.lines.forEach { line ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, UUID.fromString(order.id))
                statement.setObject(3, UUID.fromString(order.eventId))
                statement.setObject(4, UUID.fromString(line.inventoryUnitId))
                statement.setString(5, line.inventoryRef)
                statement.setString(6, line.label)
                statement.setString(7, "incomedy.ticket.v1:${UUID.randomUUID()}")
                statement.setObject(8, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Довыпускает билеты для уже оплаченных заказов пользователя, которые появились до новой схемы. */
    private fun ensurePaidTicketsIssuedForUser(
        connection: Connection,
        userId: String,
        now: OffsetDateTime,
    ) {
        val paidOrderIds = connection.prepareStatement(
            """
            SELECT o.id
            FROM ticket_orders o
            WHERE o.user_id = ?
              AND o.status = 'paid'
            ORDER BY o.id
            FOR UPDATE OF o
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.getObject("id").toString())
                    }
                }
            }
        }
        paidOrderIds.forEach { paidOrderId ->
            issueTicketsForOrder(
                connection = connection,
                orderId = paidOrderId,
                now = now,
            )
        }
    }

    /** Освобождает только те inventory unit-ы, которые все еще удерживаются локально как `pending_payment`. */
    private fun releasePendingPaymentInventory(
        connection: Connection,
        inventoryUnitIds: List<String>,
    ) {
        if (inventoryUnitIds.isEmpty()) return
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                active_hold_id = NULL,
                status = base_status,
                updated_at = NOW()
            WHERE id = ?
              AND status = 'pending_payment'
            """.trimIndent(),
        ).use { statement ->
            inventoryUnitIds.forEach { inventoryUnitId ->
                statement.setObject(1, UUID.fromString(inventoryUnitId))
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Истекает hold и возвращает inventory unit к ее базовой доступности. */
    private fun expireHold(
        connection: Connection,
        inventoryUnitId: String,
        holdId: String,
    ) {
        connection.prepareStatement(
            """
            UPDATE seat_holds
            SET
                status = 'expired',
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                active_hold_id = NULL,
                status = base_status,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(inventoryUnitId))
            statement.executeUpdate()
        }
    }

    /** Загружает hold-ы для checkout, сериализуя их через inventory row locks. */
    private fun loadCheckoutHoldRows(
        connection: Connection,
        holdIds: List<String>,
    ): List<CheckoutLockedHoldRow> {
        val placeholders = holdIds.joinToString(", ") { "?" }
        return connection.prepareStatement(
            """
            SELECT
                h.id AS hold_id,
                h.event_id,
                h.inventory_unit_id,
                h.user_id,
                h.expires_at,
                h.status AS hold_status,
                i.inventory_ref,
                i.label,
                i.price_minor,
                i.currency,
                i.status AS inventory_status,
                i.active_hold_id
            FROM seat_holds h
            JOIN ticket_inventory_units i
              ON i.id = h.inventory_unit_id
            WHERE h.id IN ($placeholders)
            ORDER BY i.id
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            holdIds.forEachIndexed { index, holdId ->
                statement.setObject(index + 1, UUID.fromString(holdId))
            }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            CheckoutLockedHoldRow(
                                holdId = result.getObject("hold_id").toString(),
                                eventId = result.getObject("event_id").toString(),
                                inventoryUnitId = result.getObject("inventory_unit_id").toString(),
                                userId = result.getObject("user_id").toString(),
                                holdExpiresAt = result.getObject("expires_at", OffsetDateTime::class.java),
                                holdStatus = result.getString("hold_status"),
                                inventoryRef = result.getString("inventory_ref"),
                                label = result.getString("label"),
                                priceMinor = result.getObject("price_minor") as Int?,
                                currency = result.getString("currency"),
                                inventoryStatus = result.getString("inventory_status"),
                                activeHoldId = result.getString("active_hold_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Загружает весь текущий inventory snapshot события вместе с привязанными активными hold-ами. */
    private fun loadInventory(
        connection: Connection,
        eventId: String,
    ): List<StoredInventoryUnit> {
        return connection.prepareStatement(
            """
            SELECT
                i.id,
                i.event_id,
                i.inventory_ref,
                i.inventory_type,
                i.snapshot_target_type,
                i.snapshot_target_ref,
                i.label,
                i.price_zone_id,
                i.price_zone_name,
                i.price_minor,
                i.currency,
                i.base_status,
                i.status,
                h.id AS hold_id,
                h.user_id AS hold_user_id,
                h.expires_at AS hold_expires_at,
                h.status AS hold_status
            FROM ticket_inventory_units i
            LEFT JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
            ORDER BY i.inventory_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.toStoredInventoryUnit())
                    }
                }
            }
        }
    }

    /** Загружает одну inventory unit, при необходимости блокируя ее для последующей мутации. */
    private fun loadInventoryUnit(
        connection: Connection,
        eventId: String,
        inventoryRef: String,
        lockForUpdate: Boolean,
    ): StoredInventoryUnit? {
        val lockClause = if (lockForUpdate) " FOR UPDATE OF i" else ""
        return connection.prepareStatement(
            """
            SELECT
                i.id,
                i.event_id,
                i.inventory_ref,
                i.inventory_type,
                i.snapshot_target_type,
                i.snapshot_target_ref,
                i.label,
                i.price_zone_id,
                i.price_zone_name,
                i.price_minor,
                i.currency,
                i.base_status,
                i.status,
                h.id AS hold_id,
                h.user_id AS hold_user_id,
                h.expires_at AS hold_expires_at,
                h.status AS hold_status
            FROM ticket_inventory_units i
            LEFT JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
              AND i.inventory_ref = ?$lockClause
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setString(2, inventoryRef)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredInventoryUnit()
            }
        }
    }

    /** Загружает hold по его id. */
    private fun loadSeatHold(
        connection: Connection,
        holdId: String,
    ): StoredSeatHold? {
        return connection.prepareStatement(
            """
            SELECT
                h.id,
                h.event_id,
                h.inventory_unit_id,
                i.inventory_ref,
                h.user_id,
                h.expires_at,
                h.status
            FROM seat_holds h
            JOIN ticket_inventory_units i
              ON i.id = h.inventory_unit_id
            WHERE h.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredSeatHold()
            }
        }
    }

    /** Загружает hold, сериализуя его через inventory row lock. */
    private fun loadSeatHoldLockedByInventory(
        connection: Connection,
        holdId: String,
    ): LockedSeatHoldRow? {
        return connection.prepareStatement(
            """
            SELECT
                h.id,
                h.event_id,
                h.inventory_unit_id,
                i.inventory_ref,
                h.user_id,
                h.expires_at,
                h.status
            FROM seat_holds h
            JOIN ticket_inventory_units i
              ON i.id = h.inventory_unit_id
            WHERE h.id = ?
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                LockedSeatHoldRow(
                    id = result.getObject("id").toString(),
                    eventId = result.getObject("event_id").toString(),
                    inventoryUnitId = result.getObject("inventory_unit_id").toString(),
                    inventoryRef = result.getString("inventory_ref"),
                    userId = result.getObject("user_id").toString(),
                    expiresAt = result.getObject("expires_at", OffsetDateTime::class.java),
                    status = result.getString("status"),
                )
            }
        }
    }

    /** Маппит текущую строку query inventory в stored model. */
    private fun java.sql.ResultSet.toStoredInventoryUnit(): StoredInventoryUnit {
        val holdId = getString("hold_id")
        return StoredInventoryUnit(
            id = getObject("id").toString(),
            eventId = getObject("event_id").toString(),
            inventoryRef = getString("inventory_ref"),
            inventoryType = getString("inventory_type"),
            snapshotTargetType = getString("snapshot_target_type"),
            snapshotTargetRef = getString("snapshot_target_ref"),
            label = getString("label"),
            priceZoneId = getString("price_zone_id"),
            priceZoneName = getString("price_zone_name"),
            priceMinor = getObject("price_minor") as Int?,
            currency = getString("currency"),
            baseStatus = getString("base_status"),
            status = getString("status"),
            activeHold = holdId?.let {
                StoredSeatHold(
                    id = it,
                    eventId = getObject("event_id").toString(),
                    inventoryUnitId = getObject("id").toString(),
                    inventoryRef = getString("inventory_ref"),
                    userId = getObject("hold_user_id").toString(),
                    expiresAt = getObject("hold_expires_at", OffsetDateTime::class.java),
                    status = getString("hold_status"),
                )
            },
        )
    }

    /** Маппит текущую строку query hold в stored hold model. */
    private fun java.sql.ResultSet.toStoredSeatHold(): StoredSeatHold {
        return StoredSeatHold(
            id = getObject("id").toString(),
            eventId = getObject("event_id").toString(),
            inventoryUnitId = getObject("inventory_unit_id").toString(),
            inventoryRef = getString("inventory_ref"),
            userId = getObject("user_id").toString(),
            expiresAt = getObject("expires_at", OffsetDateTime::class.java),
            status = getString("status"),
        )
    }

    /** Маппит текущую строку query билета в stored model. */
    private fun java.sql.ResultSet.toStoredIssuedTicket(): StoredIssuedTicket {
        return StoredIssuedTicket(
            id = getObject("id").toString(),
            orderId = getObject("order_id").toString(),
            eventId = getObject("event_id").toString(),
            inventoryUnitId = getObject("inventory_unit_id").toString(),
            inventoryRef = getString("inventory_ref"),
            label = getString("label"),
            status = getString("status"),
            qrPayload = getString("qr_payload"),
            issuedAt = getObject("issued_at", OffsetDateTime::class.java),
            checkedInAt = getObject("checked_in_at", OffsetDateTime::class.java),
            checkedInByUserId = getObject("checked_in_by_user_id")?.toString(),
        )
    }
}

/**
 * Lock-carrier для release flow, которому нужен и hold, и базовый статус inventory unit.
 *
 * @property id Идентификатор hold-а.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property inventoryRef Стабильная ссылка inventory unit.
 * @property userId Идентификатор владельца hold-а.
 * @property expiresAt Момент истечения hold-а.
 * @property status Текущий статус hold-а.
 */
private data class LockedSeatHoldRow(
    val id: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val userId: String,
    val expiresAt: OffsetDateTime,
    val status: String,
)

/**
 * Lock-carrier для checkout creation, содержащий и hold, и текущий inventory snapshot unit-а.
 *
 * @property holdId Идентификатор hold-а.
 * @property eventId Идентификатор события hold-а.
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property userId Идентификатор владельца hold-а.
 * @property holdExpiresAt Момент истечения hold-а.
 * @property holdStatus Текущий статус hold-а.
 * @property inventoryRef Стабильная ссылка inventory unit.
 * @property label Человекочитаемая подпись inventory unit.
 * @property priceMinor Текущая цена inventory unit.
 * @property currency Валюта inventory unit.
 * @property inventoryStatus Текущее состояние inventory unit.
 * @property activeHoldId Идентификатор активного hold-а, если он еще привязан к unit.
 */
private data class CheckoutLockedHoldRow(
    val holdId: String,
    val eventId: String,
    val inventoryUnitId: String,
    val userId: String,
    val holdExpiresAt: OffsetDateTime,
    val holdStatus: String,
    val inventoryRef: String,
    val label: String,
    val priceMinor: Int?,
    val currency: String,
    val inventoryStatus: String,
    val activeHoldId: String?,
)

/**
 * Lock-carrier для payment confirmation, содержащий текущее состояние inventory unit заказа.
 *
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property inventoryRef Стабильная ссылка inventory unit.
 * @property status Текущее состояние inventory unit.
 * @property baseStatus Базовая доступность unit-а вне hold/payment flow.
 */
private data class LockedOrderInventoryRow(
    val inventoryUnitId: String,
    val inventoryRef: String,
    val status: String,
    val baseStatus: String,
)
