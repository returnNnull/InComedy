package com.bam.incomedy.server.ticketing

import com.bam.incomedy.domain.event.EventAvailabilityOverride
import com.bam.incomedy.domain.event.EventAvailabilityStatus
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.ticketing.TicketingInventoryCompiler
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredEventPricingAssignment
import com.bam.incomedy.server.db.StoredInventoryUnit
import com.bam.incomedy.server.db.StoredIssuedTicket
import com.bam.incomedy.server.db.StoredInventoryUnitBlueprint
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.db.StoredSeatHold
import com.bam.incomedy.server.db.StoredTicketCheckInResult
import com.bam.incomedy.server.db.StoredTicketCheckoutSession
import com.bam.incomedy.server.db.StoredTicketCheckoutState
import com.bam.incomedy.server.db.StoredTicketOrder
import com.bam.incomedy.server.db.TicketingInventoryConflictPersistenceException
import com.bam.incomedy.server.db.TicketingInventoryUnitNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutConflictPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutCurrencyMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldEventMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldPermissionDeniedPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutPaymentRecoveryRequiredPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutPriceMissingPersistenceException
import com.bam.incomedy.server.db.TicketingRepository
import com.bam.incomedy.server.db.TicketingSeatHoldInactivePersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldPermissionDeniedPersistenceException
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository
import com.bam.incomedy.server.events.decodeStoredSnapshotLayout
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Ticketing foundation orchestration поверх organizer events и derived inventory.
 *
 * Сервис проверяет visibility/sales policy, пересобирает inventory из frozen snapshot-а и
 * прокидывает hold transitions и provider-agnostic checkout order foundation в persistence.
 */
class EventTicketingService(
    private val workspaceRepository: WorkspaceRepository,
    private val eventRepository: EventRepository,
    private val ticketingRepository: TicketingRepository,
    private val checkoutGateway: TicketCheckoutGateway? = null,
    private val nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
    private val holdTtl: Duration = Duration.ofMinutes(10),
    private val checkoutTtl: Duration = Duration.ofMinutes(10),
) {
    /** Возвращает публичный derived inventory только для опубликованного public-события. */
    fun listPublicInventory(eventId: String): List<StoredInventoryUnit> {
        return listInventoryForEvent(
            event = loadPublicTicketingEvent(eventId = eventId),
        )
    }

    /** Возвращает текущий derived inventory опубликованного события. */
    fun listInventory(
        actorUserId: String,
        eventId: String,
    ): List<StoredInventoryUnit> {
        return listInventoryForEvent(
            event = loadProtectedTicketingEvent(
                actorUserId = actorUserId,
                eventId = eventId,
            ),
        )
    }

    /**
     * Возвращает актуальный инвентарь события, выполняя derive/sync только при изменении
     * organizer event revision или на первом bootstrap чтении.
     */
    private fun listInventoryForEvent(
        event: StoredOrganizerEvent,
    ): List<StoredInventoryUnit> {
        val now = nowProvider()
        return if (ticketingRepository.isInventorySynchronized(
                eventId = event.id,
                sourceEventUpdatedAt = event.updatedAt,
            )
        ) {
            ticketingRepository.listInventory(
                eventId = event.id,
                now = now,
            )
        } else {
            ticketingRepository.synchronizeInventory(
                eventId = event.id,
                inventory = deriveInventory(event),
                sourceEventUpdatedAt = event.updatedAt,
                now = now,
            )
        }
    }

    /** Создает новый hold, если событие опубликовано и продажи открыты. */
    fun createSeatHold(
        actorUserId: String,
        eventId: String,
        inventoryRef: String,
    ): StoredSeatHold {
        val event = loadProtectedTicketingEvent(
            actorUserId = actorUserId,
            eventId = eventId,
        )
        if (event.salesStatus != EventSalesStatus.OPEN.wireName) {
            throw TicketingConflictException("Открытые продажи требуются для создания hold-а")
        }
        val now = nowProvider()
        ensureInventorySynchronized(
            event = event,
            now = now,
        )
        return try {
            ticketingRepository.createSeatHold(
                eventId = event.id,
                inventoryRef = inventoryRef,
                userId = actorUserId,
                expiresAt = now.plus(holdTtl),
                now = now,
            )
        } catch (error: TicketingInventoryUnitNotFoundPersistenceException) {
            throw TicketingInventoryUnitNotFoundException(
                eventId = error.eventId,
                inventoryRef = error.inventoryRef,
            )
        } catch (error: TicketingInventoryConflictPersistenceException) {
            throw TicketingConflictException(
                "Inventory unit недоступна для hold-а в текущем состоянии: ${error.currentStatus}",
            )
        }
    }

    /** Создает checkout order из активных hold-ов текущего пользователя. */
    fun createTicketOrder(
        actorUserId: String,
        eventId: String,
        holdIds: List<String>,
    ): StoredTicketOrder {
        if (holdIds.isEmpty()) {
            throw TicketingValidationException("Для checkout нужен хотя бы один hold")
        }
        if (holdIds.size != holdIds.distinct().size) {
            throw TicketingValidationException("Hold id не должен повторяться в одном checkout request")
        }
        val event = loadProtectedTicketingEvent(
            actorUserId = actorUserId,
            eventId = eventId,
        )
        if (event.salesStatus != EventSalesStatus.OPEN.wireName) {
            throw TicketingConflictException("Открытые продажи требуются для создания checkout order-а")
        }
        val now = nowProvider()
        ensureInventorySynchronized(
            event = event,
            now = now,
        )
        return try {
            ticketingRepository.createTicketOrder(
                eventId = event.id,
                holdIds = holdIds,
                userId = actorUserId,
                checkoutExpiresAt = now.plus(checkoutTtl),
                now = now,
            )
        } catch (error: TicketingSeatHoldNotFoundPersistenceException) {
            throw TicketingSeatHoldNotFoundException(error.holdId)
        } catch (_: TicketingCheckoutHoldPermissionDeniedPersistenceException) {
            throw TicketingSeatHoldForbiddenException("checkout_hold_forbidden")
        } catch (_: TicketingCheckoutHoldEventMismatchPersistenceException) {
            throw TicketingValidationException("Все hold-ы должны принадлежать выбранному событию")
        } catch (error: TicketingCheckoutConflictPersistenceException) {
            throw TicketingConflictException(
                when (error.reasonCode) {
                    "hold_inactive" -> "Checkout order можно собрать только из активных hold-ов"
                    "hold_expired" -> "Один из hold-ов уже истек"
                    else -> "Checkout order нельзя собрать из выбранных hold-ов"
                },
            )
        } catch (error: TicketingCheckoutPriceMissingPersistenceException) {
            throw TicketingValidationException(
                "У одной из inventory unit нет зафиксированной цены: ${error.inventoryRef}",
            )
        } catch (error: TicketingCheckoutCurrencyMismatchPersistenceException) {
            throw TicketingValidationException(
                "Checkout order не поддерживает смешанные валюты: ${error.expectedCurrency} и ${error.actualCurrency}",
            )
        }
    }

    /** Возвращает checkout order текущего пользователя с учетом авто-истечения pending lock-а. */
    fun getTicketOrder(
        actorUserId: String,
        orderId: String,
    ): StoredTicketOrder {
        val order = ticketingRepository.findTicketOrder(
            orderId = orderId,
            now = nowProvider(),
        ) ?: throw TicketingTicketOrderNotFoundException(orderId)
        if (order.userId != actorUserId) {
            throw TicketingTicketOrderNotFoundException(orderId)
        }
        return order
    }

    /** Возвращает выданные билеты текущего пользователя. */
    fun listMyTickets(
        actorUserId: String,
    ): List<StoredIssuedTicket> {
        return ticketingRepository.listIssuedTickets(
            userId = actorUserId,
            now = nowProvider(),
        )
    }

    /** Стартует внешний checkout для ожидающего оплаты ticket order-а текущего пользователя. */
    fun startTicketCheckout(
        actorUserId: String,
        eventId: String,
        orderId: String,
        requestId: String,
    ): StoredTicketCheckoutSession {
        val event = loadProtectedTicketingEvent(
            actorUserId = actorUserId,
            eventId = eventId,
        )
        val now = nowProvider()
        val order = ticketingRepository.findTicketOrder(
            orderId = orderId,
            now = now,
        ) ?: throw TicketingTicketOrderNotFoundException(orderId)
        if (order.eventId != event.id || order.userId != actorUserId) {
            throw TicketingTicketOrderNotFoundException(orderId)
        }
        when (order.status) {
            "awaiting_payment" -> Unit
            "expired" -> throw TicketingConflictException("Checkout order уже истек")
            "paid" -> throw TicketingConflictException("Checkout order уже оплачен")
            "canceled" -> throw TicketingConflictException("Checkout order уже отменен")
            else -> throw TicketingConflictException("Checkout order недоступен в состоянии ${order.status}")
        }
        ticketingRepository.findTicketCheckoutSession(orderId)?.let { existing ->
            return existing
        }
        val activeGateway = checkoutGateway
            ?: throw TicketingCheckoutUnavailableException("checkout_unavailable")
        val gatewayResponse = activeGateway.createCheckoutSession(
            TicketCheckoutGatewayRequest(
                orderId = order.id,
                eventId = order.eventId,
                currency = order.currency,
                totalMinor = order.totalMinor,
                description = "InComedy order ${order.id.take(8)}",
                requestId = requestId,
            ),
        )
        return ticketingRepository.createTicketCheckoutSession(
            orderId = order.id,
            provider = gatewayResponse.provider,
            providerPaymentId = gatewayResponse.providerPaymentId,
            providerStatus = gatewayResponse.providerStatus,
            confirmationUrl = gatewayResponse.confirmationUrl,
            returnUrl = gatewayResponse.returnUrl,
            checkoutExpiresAt = order.checkoutExpiresAt,
            now = now,
        )
    }

    /**
     * Обрабатывает payment webhook через актуальный provider snapshot, а не через сырой payload.
     *
     * Такой подход не доверяет входящему webhook без перепроверки: сначала сопоставляется локальная
     * checkout session, затем из PSP считывается текущее состояние платежа, и только после этого
     * применяются идемпотентные переходы order/session/inventory.
     */
    fun handleTicketCheckoutWebhook(
        providerPaymentId: String,
    ): TicketCheckoutWebhookOutcome {
        val activeGateway = checkoutGateway
            ?: throw TicketingCheckoutUnavailableException("checkout_unavailable")
        val now = nowProvider()
        val state = ticketingRepository.findTicketCheckoutStateByProviderPaymentId(
            provider = activeGateway.provider,
            providerPaymentId = providerPaymentId,
            now = now,
        ) ?: return TicketCheckoutWebhookOutcome.Ignored(reasonCode = "checkout_session_missing")
        val payment = activeGateway.getPayment(providerPaymentId)
        if (payment.orderId != state.order.id ||
            payment.eventId != state.order.eventId ||
            payment.totalMinor != state.order.totalMinor ||
            payment.currency != state.order.currency
        ) {
            return TicketCheckoutWebhookOutcome.RecoveryRequired(
                orderId = state.order.id,
                reasonCode = "payment_mismatch",
            )
        }
        return try {
            when (payment.status) {
                "succeeded" -> ticketingRepository.markTicketCheckoutSucceeded(
                    provider = activeGateway.provider,
                    providerPaymentId = providerPaymentId,
                    providerStatus = payment.status,
                    now = now,
                )?.toAppliedOutcome(resultCode = "paid")
                    ?: TicketCheckoutWebhookOutcome.Ignored(reasonCode = "checkout_session_missing")

                "canceled" -> ticketingRepository.markTicketCheckoutCanceled(
                    provider = activeGateway.provider,
                    providerPaymentId = providerPaymentId,
                    providerStatus = payment.status,
                    now = now,
                )?.toAppliedOutcome(resultCode = "canceled")
                    ?: TicketCheckoutWebhookOutcome.Ignored(reasonCode = "checkout_session_missing")

                "waiting_for_capture" -> ticketingRepository.markTicketCheckoutWaitingForCapture(
                    provider = activeGateway.provider,
                    providerPaymentId = providerPaymentId,
                    providerStatus = payment.status,
                    now = now,
                )?.toAppliedOutcome(resultCode = "waiting_for_capture")
                    ?: TicketCheckoutWebhookOutcome.Ignored(reasonCode = "checkout_session_missing")

                else -> TicketCheckoutWebhookOutcome.Ignored(reasonCode = "provider_status_unsupported")
            }
        } catch (error: TicketingCheckoutPaymentRecoveryRequiredPersistenceException) {
            TicketCheckoutWebhookOutcome.RecoveryRequired(
                orderId = error.orderId,
                reasonCode = error.reasonCode,
            )
        }
    }

    /** Проверяет билет по QR и идемпотентно фиксирует проход для owner/manager/checker. */
    fun scanTicket(
        actorUserId: String,
        qrPayload: String,
    ): StoredTicketCheckInResult {
        val ticket = ticketingRepository.findIssuedTicketByQrPayload(
            qrPayload = qrPayload,
            now = nowProvider(),
        ) ?: throw TicketingIssuedTicketNotFoundException(qrPayload)
        if (ticket.status == "canceled") {
            throw TicketingConflictException("Билет недействителен")
        }
        val event = eventRepository.findEvent(ticket.eventId)
            ?: throw TicketingEventNotFoundException(ticket.eventId)
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = event.workspaceId,
            userId = actorUserId,
        ) ?: throw TicketingCheckInForbiddenException("checkin_forbidden")
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER &&
            access.permissionRole != WorkspacePermissionRole.CHECKER
        ) {
            throw TicketingCheckInForbiddenException("checkin_forbidden")
        }
        return ticketingRepository.markIssuedTicketCheckedIn(
            ticketId = ticket.id,
            checkedInByUserId = actorUserId,
            now = nowProvider(),
        ) ?: throw TicketingIssuedTicketNotFoundException(ticket.id)
    }

    /**
     * Выполняет derived inventory sync только если organizer event реально изменился после прошлого
     * reconcile-а или inventory еще не инициализирован.
     */
    private fun ensureInventorySynchronized(
        event: StoredOrganizerEvent,
        now: OffsetDateTime,
    ) {
        if (ticketingRepository.isInventorySynchronized(
                eventId = event.id,
                sourceEventUpdatedAt = event.updatedAt,
            )
        ) {
            return
        }
        ticketingRepository.synchronizeInventory(
            eventId = event.id,
            inventory = deriveInventory(event),
            sourceEventUpdatedAt = event.updatedAt,
            now = now,
        )
    }

    /** Освобождает hold текущего пользователя. */
    fun releaseSeatHold(
        actorUserId: String,
        holdId: String,
    ): StoredSeatHold {
        return try {
            ticketingRepository.releaseSeatHold(
                holdId = holdId,
                userId = actorUserId,
                now = nowProvider(),
            )
        } catch (_: TicketingSeatHoldNotFoundPersistenceException) {
            throw TicketingSeatHoldNotFoundException(holdId)
        } catch (_: TicketingSeatHoldPermissionDeniedPersistenceException) {
            throw TicketingSeatHoldForbiddenException("hold_release_forbidden")
        } catch (error: TicketingSeatHoldInactivePersistenceException) {
            throw TicketingConflictException(
                "Hold уже неактивен: ${error.currentStatus}",
            )
        }
    }

    /** Загружает опубликованное public-событие для audience inventory surface. */
    private fun loadPublicTicketingEvent(
        eventId: String,
    ): StoredOrganizerEvent {
        val event = eventRepository.findEvent(eventId)
            ?: throw TicketingEventNotFoundException(eventId)
        if (event.status != EventStatus.PUBLISHED.wireName) {
            throw TicketingEventUnavailableException("inventory_unavailable")
        }
        val visibility = EventVisibility.fromWireName(event.visibility)
            ?: throw IllegalStateException("Unknown event visibility: ${event.visibility}")
        if (visibility != EventVisibility.PUBLIC) {
            throw TicketingEventUnavailableException("inventory_unavailable")
        }
        return event
    }

    /** Загружает опубликованное событие и проверяет, доступно ли оно текущему actor-у. */
    private fun loadProtectedTicketingEvent(
        actorUserId: String,
        eventId: String,
    ): StoredOrganizerEvent {
        val event = loadPublicOrPublishedEvent(eventId = eventId)
        val visibility = EventVisibility.fromWireName(event.visibility)
            ?: throw IllegalStateException("Unknown event visibility: ${event.visibility}")
        if (visibility == EventVisibility.PUBLIC) return event
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = event.workspaceId,
            userId = actorUserId,
        ) ?: throw TicketingEventUnavailableException("inventory_unavailable")
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER
        ) {
            throw TicketingEventUnavailableException("inventory_unavailable")
        }
        return event
    }

    /** Загружает опубликованное событие без решения вопроса о приватном доступе. */
    private fun loadPublicOrPublishedEvent(eventId: String): StoredOrganizerEvent {
        val event = eventRepository.findEvent(eventId)
            ?: throw TicketingEventNotFoundException(eventId)
        if (event.status != EventStatus.PUBLISHED.wireName) {
            throw TicketingEventUnavailableException("inventory_unavailable")
        }
        return event
    }

    /** Преобразует organizer event в набор persistence-ready inventory blueprints. */
    private fun deriveInventory(event: StoredOrganizerEvent): List<StoredInventoryUnitBlueprint> {
        return TicketingInventoryCompiler.compile(event.toDomain()).map { unit ->
            StoredInventoryUnitBlueprint(
                eventId = unit.eventId,
                inventoryRef = unit.inventoryRef,
                inventoryType = unit.inventoryType.wireName,
                snapshotTargetType = unit.snapshotTargetType.wireName,
                snapshotTargetRef = unit.snapshotTargetRef,
                label = unit.label,
                priceZoneId = unit.priceZoneId,
                priceZoneName = unit.priceZoneName,
                priceMinor = unit.priceMinor,
                currency = unit.currency,
                baseStatus = unit.baseStatus.wireName,
            )
        }
    }

    /** Маппит stored organizer event в domain model для inventory compiler-а. */
    private fun StoredOrganizerEvent.toDomain(): OrganizerEvent {
        return OrganizerEvent(
            id = id,
            workspaceId = workspaceId,
            venueId = venueId,
            venueName = venueName,
            hallSnapshotId = hallSnapshot.id,
            sourceTemplateId = hallSnapshot.sourceTemplateId,
            sourceTemplateName = hallSnapshot.sourceTemplateName,
            title = title,
            description = description,
            startsAtIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(startsAt),
            doorsOpenAtIso = doorsOpenAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
            endsAtIso = endsAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
            status = EventStatus.fromWireName(status)
                ?: throw IllegalStateException("Unknown event status: $status"),
            salesStatus = EventSalesStatus.fromWireName(salesStatus)
                ?: throw IllegalStateException("Unknown event sales status: $salesStatus"),
            currency = currency,
            visibility = EventVisibility.fromWireName(visibility)
                ?: throw IllegalStateException("Unknown event visibility: $visibility"),
            hallSnapshot = com.bam.incomedy.domain.event.EventHallSnapshot(
                id = hallSnapshot.id,
                eventId = id,
                sourceTemplateId = hallSnapshot.sourceTemplateId,
                layout = decodeStoredSnapshotLayout(hallSnapshot.snapshotJson).toDomain(),
            ),
            priceZones = priceZones.map { zone -> zone.toDomain() },
            pricingAssignments = pricingAssignments.map { assignment -> assignment.toDomain() },
            availabilityOverrides = availabilityOverrides.map { availabilityOverride ->
                availabilityOverride.toDomain()
            },
        )
    }

    /** Маппит stored event price zone в domain model. */
    private fun StoredEventPriceZone.toDomain(): EventPriceZone {
        return EventPriceZone(
            id = id,
            name = name,
            priceMinor = priceMinor,
            currency = currency,
            salesStartAtIso = salesStartAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
            salesEndAtIso = salesEndAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
            sourceTemplatePriceZoneId = sourceTemplatePriceZoneId,
        )
    }

    /** Маппит stored pricing assignment в domain model. */
    private fun StoredEventPricingAssignment.toDomain(): EventPricingAssignment {
        return EventPricingAssignment(
            targetType = EventOverrideTargetType.fromWireName(targetType)
                ?: throw IllegalStateException("Unknown target type: $targetType"),
            targetRef = targetRef,
            eventPriceZoneId = eventPriceZoneId,
        )
    }

    /** Маппит stored availability override в domain model. */
    private fun StoredEventAvailabilityOverride.toDomain(): EventAvailabilityOverride {
        return EventAvailabilityOverride(
            targetType = EventOverrideTargetType.fromWireName(targetType)
                ?: throw IllegalStateException("Unknown target type: $targetType"),
            targetRef = targetRef,
            availabilityStatus = EventAvailabilityStatus.fromWireName(availabilityStatus)
                ?: throw IllegalStateException("Unknown availability status: $availabilityStatus"),
        )
    }
}

/** Сигнализирует, что событие не найдено. */
class TicketingEventNotFoundException(
    val eventId: String,
) : IllegalStateException("Event was not found")

/** Сигнализирует, что inventory surface события пока недоступна. */
class TicketingEventUnavailableException(
    val reasonCode: String,
) : IllegalStateException("Event inventory is not available")

/** Сигнализирует, что hold не найден. */
class TicketingSeatHoldNotFoundException(
    val holdId: String,
) : IllegalStateException("Seat hold was not found")

/** Сигнализирует, что checkout order не найден или недоступен текущему пользователю. */
class TicketingTicketOrderNotFoundException(
    val orderId: String,
) : IllegalStateException("Ticket order was not found")

/** Сигнализирует, что выданный билет не найден. */
class TicketingIssuedTicketNotFoundException(
    val ticketKey: String,
) : IllegalStateException("Issued ticket was not found")

/** Сигнализирует, что hold нельзя освободить из-за отсутствия прав. */
class TicketingSeatHoldForbiddenException(
    val reasonCode: String,
) : IllegalStateException("Seat hold release is forbidden")

/** Сигнализирует, что текущий пользователь не может выполнять check-in для этого события. */
class TicketingCheckInForbiddenException(
    val reasonCode: String,
) : IllegalStateException("Ticket check-in is forbidden")

/** Сигнализирует, что внешний checkout сейчас недоступен из-за отсутствия PSP-конфига. */
class TicketingCheckoutUnavailableException(
    val reasonCode: String,
) : IllegalStateException("Ticket checkout is unavailable")

/** Сигнализирует о бизнес-конфликте ticketing slice-а. */
class TicketingConflictException(
    safeMessage: String,
) : IllegalStateException(safeMessage)

/** Сигнализирует о некорректном запросе ticketing slice-а. */
class TicketingValidationException(
    safeMessage: String,
) : IllegalStateException(safeMessage)

/** Сигнализирует, что конкретная inventory unit не найдена. */
class TicketingInventoryUnitNotFoundException(
    val eventId: String,
    val inventoryRef: String,
) : IllegalStateException("Inventory unit was not found")

/**
 * Результат синхронизации локального ticketing state-а с внешним payment webhook.
 */
sealed interface TicketCheckoutWebhookOutcome {
    /**
     * Webhook успешно применен к локальному order/session state.
     *
     * @property order Обновленный локальный order.
     * @property session Обновленная checkout session.
     * @property resultCode Низкокардинальный код успешного перехода.
     */
    data class Applied(
        val order: StoredTicketOrder,
        val session: StoredTicketCheckoutSession,
        val resultCode: String,
    ) : TicketCheckoutWebhookOutcome

    /**
     * Уведомление принято, но локальное состояние изменять не пришлось.
     *
     * @property reasonCode Низкокардинальная причина пропуска.
     */
    data class Ignored(
        val reasonCode: String,
    ) : TicketCheckoutWebhookOutcome

    /**
     * Автоматически применить успешный webhook небезопасно и нужен recovery flow/оператор.
     *
     * @property orderId Локальный order, если он был найден.
     * @property reasonCode Низкокардинальная причина перехода в recovery.
     */
    data class RecoveryRequired(
        val orderId: String?,
        val reasonCode: String,
    ) : TicketCheckoutWebhookOutcome
}

/** Преобразует объединенное checkout state в успешный webhook outcome. */
private fun StoredTicketCheckoutState.toAppliedOutcome(
    resultCode: String,
): TicketCheckoutWebhookOutcome.Applied {
    return TicketCheckoutWebhookOutcome.Applied(
        order = order,
        session = session,
        resultCode = resultCode,
    )
}
