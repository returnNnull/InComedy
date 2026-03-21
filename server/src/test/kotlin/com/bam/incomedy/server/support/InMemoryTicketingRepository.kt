package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.StoredInventoryUnit
import com.bam.incomedy.server.db.StoredInventoryUnitBlueprint
import com.bam.incomedy.server.db.StoredSeatHold
import com.bam.incomedy.server.db.StoredTicketCheckoutSession
import com.bam.incomedy.server.db.StoredTicketCheckoutState
import com.bam.incomedy.server.db.StoredTicketOrder
import com.bam.incomedy.server.db.StoredTicketOrderLine
import com.bam.incomedy.server.db.TicketingCheckoutConflictPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutCurrencyMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldEventMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldPermissionDeniedPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutPaymentRecoveryRequiredPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutPriceMissingPersistenceException
import com.bam.incomedy.server.db.TicketingInventoryConflictPersistenceException
import com.bam.incomedy.server.db.TicketingInventoryUnitNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingRepository
import com.bam.incomedy.server.db.TicketingSeatHoldInactivePersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldPermissionDeniedPersistenceException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация `TicketingRepository` для route/service тестов ticketing foundation.
 *
 * Репозиторий хранит derived inventory и hold-ы без реальной БД, чтобы детерминированно
 * проверять expiry, release и conflict semantics поверх одного процесса.
 */
class InMemoryTicketingRepository : TicketingRepository {
    /** Mutable inventory units по event и inventory ref. */
    private val inventoryByEventId = linkedMapOf<String, LinkedHashMap<String, MutableInventoryRecord>>()

    /** Последняя organizer revision, с которой уже синхронизирован inventory конкретного события. */
    private val syncedRevisionByEventId = linkedMapOf<String, OffsetDateTime>()

    /** Hold-ы по их id. */
    private val holdsById = linkedMapOf<String, MutableSeatHoldRecord>()

    /** Checkout order-ы по их id. */
    private val ordersById = linkedMapOf<String, MutableTicketOrderRecord>()

    /** Checkout session-ы по их order id. */
    private val checkoutSessionsByOrderId = linkedMapOf<String, MutableTicketCheckoutSessionRecord>()

    /** Счетчик inventory sync invocation-ов для route/service regression тестов. */
    var synchronizeInventoryCallCount: Int = 0
        private set

    override fun isInventorySynchronized(
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ): Boolean {
        return syncedRevisionByEventId[eventId] == sourceEventUpdatedAt
    }

    override fun synchronizeInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        sourceEventUpdatedAt: OffsetDateTime,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        synchronizeInventoryCallCount += 1
        expireOverdueOrders(
            eventId = eventId,
            now = now,
        )
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        val eventInventory = inventoryByEventId.getOrPut(eventId) { linkedMapOf() }
        inventory.forEach { blueprint ->
            val existing = eventInventory[blueprint.inventoryRef]
            if (existing == null) {
                eventInventory[blueprint.inventoryRef] = MutableInventoryRecord(
                    id = UUID.randomUUID().toString(),
                    eventId = blueprint.eventId,
                    inventoryRef = blueprint.inventoryRef,
                    inventoryType = blueprint.inventoryType,
                    snapshotTargetType = blueprint.snapshotTargetType,
                    snapshotTargetRef = blueprint.snapshotTargetRef,
                    label = blueprint.label,
                    priceZoneId = blueprint.priceZoneId,
                    priceZoneName = blueprint.priceZoneName,
                    priceMinor = blueprint.priceMinor,
                    currency = blueprint.currency,
                    baseStatus = blueprint.baseStatus,
                    status = blueprint.baseStatus,
                )
            } else {
                existing.inventoryType = blueprint.inventoryType
                existing.snapshotTargetType = blueprint.snapshotTargetType
                existing.snapshotTargetRef = blueprint.snapshotTargetRef
                existing.label = blueprint.label
                existing.priceZoneId = blueprint.priceZoneId
                existing.priceZoneName = blueprint.priceZoneName
                existing.priceMinor = blueprint.priceMinor
                existing.currency = blueprint.currency
                existing.baseStatus = blueprint.baseStatus
                existing.status = when {
                    existing.activeHoldId != null -> "held"
                    existing.status == "pending_payment" -> "pending_payment"
                    existing.status == "sold" -> "sold"
                    else -> blueprint.baseStatus
                }
            }
        }
        syncedRevisionByEventId[eventId] = sourceEventUpdatedAt
        return eventInventory.toStoredInventory()
    }

    override fun listInventory(
        eventId: String,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        expireOverdueOrders(
            eventId = eventId,
            now = now,
        )
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        return inventoryByEventId[eventId].orEmpty().toStoredInventory()
    }

    override fun createSeatHold(
        eventId: String,
        inventoryRef: String,
        userId: String,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredSeatHold {
        expireOverdueOrders(
            eventId = eventId,
            now = now,
        )
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        val eventInventory = inventoryByEventId[eventId]
        val inventoryRecord = eventInventory?.get(inventoryRef)
            ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                eventId = eventId,
                inventoryRef = inventoryRef,
            )
        inventoryRecord.activeHoldId?.let { holdId ->
            val hold = holdsById[holdId]
            if (hold != null && hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                expireHold(
                    inventoryRecord = inventoryRecord,
                    hold = hold,
                )
            }
        }
        if (inventoryRecord.status != "available") {
            throw TicketingInventoryConflictPersistenceException(
                inventoryRef = inventoryRef,
                currentStatus = inventoryRecord.status,
            )
        }
        val hold = MutableSeatHoldRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            inventoryUnitId = inventoryRecord.id,
            inventoryRef = inventoryRef,
            userId = userId,
            expiresAt = expiresAt,
            status = "active",
        )
        holdsById[hold.id] = hold
        inventoryRecord.activeHoldId = hold.id
        inventoryRecord.status = "held"
        return hold.toStored()
    }

    override fun createTicketOrder(
        eventId: String,
        holdIds: List<String>,
        userId: String,
        checkoutExpiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredTicketOrder {
        expireOverdueOrders(
            eventId = eventId,
            now = now,
        )
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        val holdRecords = holdIds.map { holdId ->
            holdsById[holdId] ?: throw TicketingSeatHoldNotFoundPersistenceException(holdId)
        }
        val orderId = UUID.randomUUID().toString()
        var orderCurrency: String? = null
        val orderLines = mutableListOf<StoredTicketOrderLine>()
        holdRecords.forEach { hold ->
            if (hold.userId != userId) {
                throw TicketingCheckoutHoldPermissionDeniedPersistenceException(
                    holdId = hold.id,
                    userId = userId,
                )
            }
            if (hold.eventId != eventId) {
                throw TicketingCheckoutHoldEventMismatchPersistenceException(
                    holdId = hold.id,
                    holdEventId = hold.eventId,
                    requestedEventId = eventId,
                )
            }
            if (hold.status != "active") {
                throw TicketingCheckoutConflictPersistenceException(
                    holdId = hold.id,
                    reasonCode = "hold_inactive",
                )
            }
            if (!hold.expiresAt.isAfter(now)) {
                val inventoryRecord = inventoryByEventId[hold.eventId]
                    ?.values
                    ?.firstOrNull { it.id == hold.inventoryUnitId }
                    ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                        eventId = hold.eventId,
                        inventoryRef = hold.inventoryRef,
                    )
                expireHold(
                    inventoryRecord = inventoryRecord,
                    hold = hold,
                )
                throw TicketingCheckoutConflictPersistenceException(
                    holdId = hold.id,
                    reasonCode = "hold_expired",
                )
            }
            val inventoryRecord = inventoryByEventId[hold.eventId]
                ?.values
                ?.firstOrNull { it.id == hold.inventoryUnitId }
                ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                    eventId = hold.eventId,
                    inventoryRef = hold.inventoryRef,
                )
            if (inventoryRecord.status != "held" || inventoryRecord.activeHoldId != hold.id) {
                throw TicketingCheckoutConflictPersistenceException(
                    holdId = hold.id,
                    reasonCode = "inventory_not_held",
                )
            }
            val priceMinor = inventoryRecord.priceMinor
                ?: throw TicketingCheckoutPriceMissingPersistenceException(inventoryRecord.inventoryRef)
            val currentCurrency = inventoryRecord.currency
            val expectedCurrency = orderCurrency
            if (expectedCurrency == null) {
                orderCurrency = currentCurrency
            } else if (expectedCurrency != currentCurrency) {
                throw TicketingCheckoutCurrencyMismatchPersistenceException(
                    holdId = hold.id,
                    expectedCurrency = expectedCurrency,
                    actualCurrency = currentCurrency,
                )
            }
            orderLines += StoredTicketOrderLine(
                orderId = orderId,
                inventoryUnitId = inventoryRecord.id,
                inventoryRef = inventoryRecord.inventoryRef,
                label = inventoryRecord.label,
                priceMinor = priceMinor,
                currency = currentCurrency,
            )
        }
        val order = MutableTicketOrderRecord(
            id = orderId,
            eventId = eventId,
            userId = userId,
            status = "awaiting_payment",
            currency = orderCurrency ?: error("Checkout order currency must be resolved"),
            totalMinor = orderLines.sumOf(StoredTicketOrderLine::priceMinor),
            checkoutExpiresAt = checkoutExpiresAt,
            lines = orderLines.toMutableList(),
        )
        ordersById[order.id] = order
        holdRecords.forEach { hold ->
            hold.status = "consumed"
            val inventoryRecord = inventoryByEventId[hold.eventId]
                ?.values
                ?.firstOrNull { it.id == hold.inventoryUnitId }
                ?: return@forEach
            inventoryRecord.activeHoldId = null
            inventoryRecord.status = "pending_payment"
        }
        return order.toStored()
    }

    override fun findTicketOrder(
        orderId: String,
        now: OffsetDateTime,
    ): StoredTicketOrder? {
        val order = ordersById[orderId] ?: return null
        expireOverdueOrders(
            eventId = order.eventId,
            now = now,
        )
        return ordersById[orderId]?.toStored()
    }

    override fun findTicketCheckoutSession(
        orderId: String,
    ): StoredTicketCheckoutSession? {
        return checkoutSessionsByOrderId[orderId]?.toStored()
    }

    override fun findTicketCheckoutStateByProviderPaymentId(
        provider: String,
        providerPaymentId: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        val session = checkoutSessionsByOrderId.values.firstOrNull { candidate ->
            candidate.provider == provider && candidate.providerPaymentId == providerPaymentId
        } ?: return null
        val order = ordersById[session.orderId] ?: return null
        return StoredTicketCheckoutState(
            order = order.toStored(),
            session = session.toStored(),
        )
    }

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
        val order = findTicketOrder(
            orderId = orderId,
            now = now,
        ) ?: error("Checkout session requires existing order")
        checkoutSessionsByOrderId[orderId]?.let { existing ->
            return existing.toStored()
        }
        val session = MutableTicketCheckoutSessionRecord(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            provider = provider,
            status = if (order.status == "expired") "expired" else "pending_redirect",
            providerPaymentId = providerPaymentId,
            providerStatus = providerStatus,
            confirmationUrl = confirmationUrl,
            returnUrl = returnUrl,
            checkoutExpiresAt = checkoutExpiresAt,
        )
        checkoutSessionsByOrderId[orderId] = session
        return session.toStored()
    }

    override fun markTicketCheckoutWaitingForCapture(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        val session = checkoutSessionsByOrderId.values.firstOrNull { candidate ->
            candidate.provider == provider && candidate.providerPaymentId == providerPaymentId
        } ?: return null
        val order = ordersById[session.orderId] ?: return null
        if (order.status == "awaiting_payment" &&
            session.status != "succeeded" &&
            session.status != "canceled"
        ) {
            session.status = "waiting_for_capture"
        }
        session.providerStatus = providerStatus
        return StoredTicketCheckoutState(
            order = order.toStored(),
            session = session.toStored(),
        )
    }

    override fun markTicketCheckoutSucceeded(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        val session = checkoutSessionsByOrderId.values.firstOrNull { candidate ->
            candidate.provider == provider && candidate.providerPaymentId == providerPaymentId
        } ?: return null
        val order = ordersById[session.orderId] ?: return null
        val inventoryRecords = order.lines.map { line ->
            inventoryByEventId[order.eventId]
                ?.values
                ?.firstOrNull { it.id == line.inventoryUnitId }
                ?: error("Inventory unit ${line.inventoryUnitId} must exist for paid checkout")
        }
        when (order.status) {
            "paid" -> Unit
            "canceled" -> throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                orderId = order.id,
                reasonCode = "order_canceled",
            )

            "awaiting_payment" -> {
                if (inventoryRecords.any { it.status in setOf("held", "sold") }) {
                    throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                        orderId = order.id,
                        reasonCode = "inventory_reassigned",
                    )
                }
            }

            "expired" -> {
                if (inventoryRecords.any { it.status in setOf("held", "pending_payment", "sold") }) {
                    throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                        orderId = order.id,
                        reasonCode = "inventory_reassigned",
                    )
                }
            }

            else -> throw TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
                orderId = order.id,
                reasonCode = "order_state_invalid",
            )
        }
        order.status = "paid"
        session.status = "succeeded"
        session.providerStatus = providerStatus
        inventoryRecords.forEach { inventoryRecord ->
            inventoryRecord.activeHoldId = null
            inventoryRecord.status = "sold"
        }
        return StoredTicketCheckoutState(
            order = order.toStored(),
            session = session.toStored(),
        )
    }

    override fun markTicketCheckoutCanceled(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState? {
        val session = checkoutSessionsByOrderId.values.firstOrNull { candidate ->
            candidate.provider == provider && candidate.providerPaymentId == providerPaymentId
        } ?: return null
        val order = ordersById[session.orderId] ?: return null
        if (order.status != "paid") {
            order.status = "canceled"
            order.lines.forEach { line ->
                val inventoryRecord = inventoryByEventId[order.eventId]
                    ?.values
                    ?.firstOrNull { it.id == line.inventoryUnitId }
                    ?: return@forEach
                if (inventoryRecord.status == "pending_payment") {
                    inventoryRecord.activeHoldId = null
                    inventoryRecord.status = inventoryRecord.baseStatus
                }
            }
        }
        session.status = if (order.status == "paid") "succeeded" else "canceled"
        session.providerStatus = providerStatus
        return StoredTicketCheckoutState(
            order = order.toStored(),
            session = session.toStored(),
        )
    }

    override fun releaseSeatHold(
        holdId: String,
        userId: String,
        now: OffsetDateTime,
    ): StoredSeatHold {
        val hold = holdsById[holdId]
            ?: throw TicketingSeatHoldNotFoundPersistenceException(holdId)
        if (hold.userId != userId) {
            throw TicketingSeatHoldPermissionDeniedPersistenceException(
                holdId = holdId,
                userId = userId,
            )
        }
        val inventoryRecord = inventoryByEventId[hold.eventId]
            ?.values
            ?.firstOrNull { it.id == hold.inventoryUnitId }
            ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                eventId = hold.eventId,
                inventoryRef = hold.inventoryRef,
            )
        if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
            expireHold(
                inventoryRecord = inventoryRecord,
                hold = hold,
            )
            throw TicketingSeatHoldInactivePersistenceException(
                holdId = holdId,
                currentStatus = "expired",
            )
        }
        if (hold.status != "active") {
            throw TicketingSeatHoldInactivePersistenceException(
                holdId = holdId,
                currentStatus = hold.status,
            )
        }
        hold.status = "released"
        inventoryRecord.activeHoldId = null
        inventoryRecord.status = inventoryRecord.baseStatus
        return hold.toStored()
    }

    /** Истекает все просроченные hold-ы конкретного события. */
    private fun expireOverdueHolds(
        eventId: String,
        now: OffsetDateTime,
    ) {
        val eventInventory = inventoryByEventId[eventId].orEmpty().values
        eventInventory.forEach { inventoryRecord ->
            val holdId = inventoryRecord.activeHoldId ?: return@forEach
            val hold = holdsById[holdId] ?: return@forEach
            if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                expireHold(
                    inventoryRecord = inventoryRecord,
                    hold = hold,
                )
            }
        }
    }

    /** Истекает pending checkout order-ы конкретного события и освобождает их inventory. */
    private fun expireOverdueOrders(
        eventId: String,
        now: OffsetDateTime,
    ) {
        ordersById.values
            .filter { order ->
                order.eventId == eventId &&
                    order.status == "awaiting_payment" &&
                    !order.checkoutExpiresAt.isAfter(now)
            }
            .forEach { order ->
                order.status = "expired"
                checkoutSessionsByOrderId[order.id]?.status = "expired"
                order.lines.forEach { line ->
                    val inventoryRecord = inventoryByEventId[eventId]
                        ?.values
                        ?.firstOrNull { it.id == line.inventoryUnitId }
                        ?: return@forEach
                    inventoryRecord.status = inventoryRecord.baseStatus
                }
            }
    }

    /** Переводит hold в expired и возвращает inventory unit к base status. */
    private fun expireHold(
        inventoryRecord: MutableInventoryRecord,
        hold: MutableSeatHoldRecord,
    ) {
        hold.status = "expired"
        inventoryRecord.activeHoldId = null
        inventoryRecord.status = inventoryRecord.baseStatus
    }

    /** Собирает read-only inventory snapshot из mutable event-local state. */
    private fun Map<String, MutableInventoryRecord>.toStoredInventory(): List<StoredInventoryUnit> {
        return values
            .sortedBy(MutableInventoryRecord::inventoryRef)
            .map { record -> record.toStored(holdsById) }
    }

    /** Mutable inventory unit record. */
    private data class MutableInventoryRecord(
        val id: String,
        val eventId: String,
        val inventoryRef: String,
        var inventoryType: String,
        var snapshotTargetType: String,
        var snapshotTargetRef: String,
        var label: String,
        var priceZoneId: String?,
        var priceZoneName: String?,
        var priceMinor: Int?,
        var currency: String,
        var baseStatus: String,
        var status: String,
        var activeHoldId: String? = null,
    ) {
        /** Преобразует mutable inventory unit в stored model. */
        fun toStored(
            holdsById: Map<String, MutableSeatHoldRecord>,
        ): StoredInventoryUnit {
            return StoredInventoryUnit(
                id = id,
                eventId = eventId,
                inventoryRef = inventoryRef,
                inventoryType = inventoryType,
                snapshotTargetType = snapshotTargetType,
                snapshotTargetRef = snapshotTargetRef,
                label = label,
                priceZoneId = priceZoneId,
                priceZoneName = priceZoneName,
                priceMinor = priceMinor,
                currency = currency,
                baseStatus = baseStatus,
                status = status,
                activeHold = activeHoldId
                    ?.let(holdsById::get)
                    ?.toStored(),
            )
        }
    }

    /** Mutable hold record. */
    private data class MutableSeatHoldRecord(
        val id: String,
        val eventId: String,
        val inventoryUnitId: String,
        val inventoryRef: String,
        val userId: String,
        val expiresAt: OffsetDateTime,
        var status: String,
    ) {
        /** Преобразует mutable hold в stored model. */
        fun toStored(): StoredSeatHold {
            return StoredSeatHold(
                id = id,
                eventId = eventId,
                inventoryUnitId = inventoryUnitId,
                inventoryRef = inventoryRef,
                userId = userId,
                expiresAt = expiresAt,
                status = status,
            )
        }
    }

    /** Mutable checkout order record. */
    private data class MutableTicketOrderRecord(
        val id: String,
        val eventId: String,
        val userId: String,
        var status: String,
        val currency: String,
        val totalMinor: Int,
        val checkoutExpiresAt: OffsetDateTime,
        val lines: MutableList<StoredTicketOrderLine>,
    ) {
        /** Преобразует mutable order в stored model. */
        fun toStored(): StoredTicketOrder {
            return StoredTicketOrder(
                id = id,
                eventId = eventId,
                userId = userId,
                status = status,
                currency = currency,
                totalMinor = totalMinor,
                checkoutExpiresAt = checkoutExpiresAt,
                lines = lines.toList(),
            )
        }
    }

    /** Mutable checkout session record. */
    private data class MutableTicketCheckoutSessionRecord(
        val id: String,
        val orderId: String,
        val provider: String,
        var status: String,
        val providerPaymentId: String,
        var providerStatus: String,
        val confirmationUrl: String,
        val returnUrl: String,
        val checkoutExpiresAt: OffsetDateTime,
    ) {
        /** Преобразует mutable checkout session в stored model. */
        fun toStored(): StoredTicketCheckoutSession {
            return StoredTicketCheckoutSession(
                id = id,
                orderId = orderId,
                provider = provider,
                status = status,
                providerPaymentId = providerPaymentId,
                providerStatus = providerStatus,
                confirmationUrl = confirmationUrl,
                returnUrl = returnUrl,
                checkoutExpiresAt = checkoutExpiresAt,
            )
        }
    }
}
