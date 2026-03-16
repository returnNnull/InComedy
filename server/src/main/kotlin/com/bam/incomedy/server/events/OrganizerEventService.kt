package com.bam.incomedy.server.events

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventOverrideValidator
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredEventPricingAssignment
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.db.VenueRepository
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Сервис organizer event management bounded context-а.
 *
 * Сервис отделяет permission policy, frozen snapshot orchestration и event-local override
 * validation от HTTP-роутов, чтобы `events` не смешивались с `ticketing`.
 *
 * @property workspaceRepository Репозиторий organizer workspace access policy.
 * @property venueRepository Репозиторий площадок и hall templates.
 * @property eventRepository Репозиторий organizer events, snapshot-ов и override-ов.
 */
class OrganizerEventService(
    private val workspaceRepository: WorkspaceRepository,
    private val venueRepository: VenueRepository,
    private val eventRepository: EventRepository,
) {
    /** Возвращает события, доступные текущему пользователю по active memberships. */
    fun listEvents(userId: String): List<StoredOrganizerEvent> = eventRepository.listEvents(userId)

    /** Возвращает одно событие с event-local overrides после проверки owner/manager scope. */
    fun getEvent(
        actorUserId: String,
        eventId: String,
    ): StoredOrganizerEvent {
        val event = eventRepository.findEvent(eventId) ?: throw EventNotFoundException(eventId)
        requireManageEventAccess(actorUserId, event.workspaceId)
        return event
    }

    /** Создает draft-событие и сразу замораживает hall snapshot выбранного template. */
    fun createEvent(
        actorUserId: String,
        draft: EventDraft,
    ): StoredOrganizerEvent {
        requireManageEventAccess(actorUserId, draft.workspaceId)
        val venue = venueRepository.findVenue(draft.venueId) ?: throw EventVenueNotFoundException(draft.venueId)
        if (venue.workspaceId != draft.workspaceId) {
            throw EventVenueScopeMismatchException(
                venueId = draft.venueId,
                workspaceId = draft.workspaceId,
            )
        }
        val template = venueRepository.findHallTemplate(draft.hallTemplateId)
            ?: throw EventTemplateNotFoundException(draft.hallTemplateId)
        if (template.venueId != draft.venueId) {
            throw EventTemplateVenueMismatchException(
                templateId = draft.hallTemplateId,
                venueId = draft.venueId,
            )
        }
        val startsAt = parseTimestamp(
            value = draft.startsAtIso,
            safeMessage = "Некорректный формат времени начала события",
        )
        val doorsOpenAt = draft.doorsOpenAtIso?.let {
            parseTimestamp(
                value = it,
                safeMessage = "Некорректный формат времени открытия дверей",
            )
        }
        val endsAt = draft.endsAtIso?.let {
            parseTimestamp(
                value = it,
                safeMessage = "Некорректный формат времени окончания события",
            )
        }
        validateChronology(
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
        )
        return eventRepository.createEvent(
            workspaceId = draft.workspaceId,
            venueId = draft.venueId,
            venueName = venue.name,
            title = draft.title.trim(),
            description = draft.description?.trim()?.takeIf(String::isNotBlank),
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
            status = EventStatus.DRAFT.wireName,
            salesStatus = EventSalesStatus.CLOSED.wireName,
            currency = draft.currency.trim().uppercase(),
            visibility = draft.visibility.wireName,
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
    }

    /** Обновляет organizer event details и event-local overrides поверх frozen snapshot. */
    fun updateEvent(
        actorUserId: String,
        eventId: String,
        draft: EventUpdateDraft,
    ): StoredOrganizerEvent {
        val event = eventRepository.findEvent(eventId) ?: throw EventNotFoundException(eventId)
        requireManageEventAccess(actorUserId, event.workspaceId)
        if (event.status == EventStatus.CANCELED.wireName) {
            throw EventValidationException("Нельзя редактировать отмененное событие")
        }
        val validationError = EventOverrideValidator.validateEventUpdateDraft(
            draft = draft,
            snapshotLayout = decodeStoredSnapshotLayout(event.hallSnapshot.snapshotJson).toDomain(),
        )
        if (validationError != null) {
            throw EventValidationException(validationError)
        }
        val startsAt = parseTimestamp(
            value = draft.startsAtIso,
            safeMessage = "Некорректный формат времени начала события",
        )
        val doorsOpenAt = draft.doorsOpenAtIso?.let {
            parseTimestamp(
                value = it,
                safeMessage = "Некорректный формат времени открытия дверей",
            )
        }
        val endsAt = draft.endsAtIso?.let {
            parseTimestamp(
                value = it,
                safeMessage = "Некорректный формат времени окончания события",
            )
        }
        validateChronology(
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
        )
        return eventRepository.updateEvent(
            eventId = eventId,
            title = draft.title.trim(),
            description = draft.description?.trim()?.takeIf(String::isNotBlank),
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
            currency = draft.currency.trim().uppercase(),
            visibility = draft.visibility.wireName,
            priceZones = draft.priceZones.map { zone ->
                StoredEventPriceZone(
                    id = zone.id,
                    name = zone.name,
                    priceMinor = zone.priceMinor,
                    currency = zone.currency,
                    salesStartAt = zone.salesStartAtIso?.let {
                        parseTimestamp(it, "Некорректный формат sales start у event price zone")
                    },
                    salesEndAt = zone.salesEndAtIso?.let {
                        parseTimestamp(it, "Некорректный формат sales end у event price zone")
                    },
                    sourceTemplatePriceZoneId = zone.sourceTemplatePriceZoneId,
                )
            },
            pricingAssignments = draft.pricingAssignments.map { assignment ->
                StoredEventPricingAssignment(
                    targetType = assignment.targetType.wireName,
                    targetRef = assignment.targetRef,
                    eventPriceZoneId = assignment.eventPriceZoneId,
                )
            },
            availabilityOverrides = draft.availabilityOverrides.map { availabilityOverride ->
                StoredEventAvailabilityOverride(
                    targetType = availabilityOverride.targetType.wireName,
                    targetRef = availabilityOverride.targetRef,
                    availabilityStatus = availabilityOverride.availabilityStatus.wireName,
                )
            },
        ) ?: throw EventNotFoundException(eventId)
    }

    /** Публикует существующий draft-событие после проверки owner/manager доступа к workspace. */
    fun publishEvent(
        actorUserId: String,
        eventId: String,
    ): StoredOrganizerEvent {
        val event = eventRepository.findEvent(eventId) ?: throw EventNotFoundException(eventId)
        requireManageEventAccess(actorUserId, event.workspaceId)
        if (event.status == EventStatus.CANCELED.wireName) {
            throw EventValidationException("Нельзя опубликовать отмененное событие")
        }
        return eventRepository.publishEvent(eventId) ?: throw EventNotFoundException(eventId)
    }

    /** Проверяет, что actor имеет owner/manager доступ к workspace события. */
    private fun requireManageEventAccess(
        actorUserId: String,
        workspaceId: String,
    ) {
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw EventScopeNotFoundException(workspaceId)
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER
        ) {
            throw EventPermissionDeniedException("event_manage_forbidden")
        }
    }

    /** Парсит ISO timestamp и возвращает безопасную domain-specific ошибку при сбое. */
    private fun parseTimestamp(
        value: String,
        safeMessage: String,
    ): OffsetDateTime {
        return try {
            OffsetDateTime.parse(value.trim())
        } catch (_: DateTimeParseException) {
            throw EventValidationException(safeMessage)
        }
    }

    /** Проверяет базовую временную логику organizer event foundation slice-а. */
    private fun validateChronology(
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
    ) {
        if (doorsOpenAt != null && doorsOpenAt.isAfter(startsAt)) {
            throw EventValidationException("Время открытия дверей должно быть не позже начала события")
        }
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw EventValidationException("Время окончания должно быть позже начала события")
        }
    }
}

/** Сигнализирует, что событие недоступно в текущем scope. */
class EventNotFoundException(
    val eventId: String,
) : IllegalStateException("Event was not found")

/** Сигнализирует, что площадка недоступна в текущем scope. */
class EventVenueNotFoundException(
    val venueId: String,
) : IllegalStateException("Venue was not found")

/** Сигнализирует, что hall template недоступен в текущем scope. */
class EventTemplateNotFoundException(
    val templateId: String,
) : IllegalStateException("Hall template was not found")

/** Сигнализирует, что workspace события недоступен текущему actor. */
class EventScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("Event workspace scope was not found")

/** Сигнализирует о запрете действия в organizer event bounded context-е. */
class EventPermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Event action is forbidden")

/** Сигнализирует о попытке привязать событие к площадке из другого workspace. */
class EventVenueScopeMismatchException(
    val venueId: String,
    val workspaceId: String,
) : IllegalStateException("Venue does not belong to workspace")

/** Сигнализирует о попытке привязать событие к шаблону другой площадки. */
class EventTemplateVenueMismatchException(
    val templateId: String,
    val venueId: String,
) : IllegalStateException("Hall template does not belong to venue")

/** Безопасная validation-ошибка organizer event surface. */
class EventValidationException(
    val safeMessage: String,
) : IllegalArgumentException(safeMessage)
