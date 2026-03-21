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
import com.bam.incomedy.server.db.StoredInventoryUnitBlueprint
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.db.StoredSeatHold
import com.bam.incomedy.server.db.StoredTicketOrder
import com.bam.incomedy.server.db.TicketingInventoryConflictPersistenceException
import com.bam.incomedy.server.db.TicketingInventoryUnitNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutConflictPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutCurrencyMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldEventMismatchPersistenceException
import com.bam.incomedy.server.db.TicketingCheckoutHoldPermissionDeniedPersistenceException
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

/** Сигнализирует, что hold нельзя освободить из-за отсутствия прав. */
class TicketingSeatHoldForbiddenException(
    val reasonCode: String,
) : IllegalStateException("Seat hold release is forbidden")

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
