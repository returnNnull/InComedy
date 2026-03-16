package com.bam.incomedy.domain.event

import com.bam.incomedy.domain.venue.HallLayout

/**
 * Контракт organizer event management bounded context-а.
 *
 * Сервис отделяет event orchestration, frozen hall snapshot flow и event-local override storage от
 * конкретного backend transport-а и задает единый API для списка, деталей и мутаций событий.
 */
interface EventManagementService {
    /** Возвращает события, доступные текущей organizer-сессии. */
    suspend fun listEvents(accessToken: String): Result<List<OrganizerEvent>>

    /** Возвращает одно событие с его frozen snapshot и event-local overrides. */
    suspend fun getEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent>

    /** Создает organizer event draft с уже привязанным snapshot схемы зала. */
    suspend fun createEvent(
        accessToken: String,
        draft: EventDraft,
    ): Result<OrganizerEvent>

    /** Обновляет event-local organizer configuration поверх frozen snapshot. */
    suspend fun updateEvent(
        accessToken: String,
        eventId: String,
        draft: EventUpdateDraft,
    ): Result<OrganizerEvent>

    /** Публикует draft-событие и фиксирует его как готовое к следующему sales slice. */
    suspend fun publishEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent>
}

/**
 * Organizer event с уже привязанным venue/template и frozen hall snapshot.
 *
 * @property id Идентификатор события.
 * @property workspaceId Workspace-владелец события.
 * @property venueId Идентификатор выбранной площадки.
 * @property venueName Человекочитаемое имя площадки.
 * @property hallSnapshotId Идентификатор frozen snapshot схемы зала.
 * @property sourceTemplateId Идентификатор исходного hall template.
 * @property sourceTemplateName Имя исходного hall template.
 * @property title Название события.
 * @property description Описание события.
 * @property startsAtIso RFC3339 timestamp начала события.
 * @property doorsOpenAtIso Необязательное время открытия дверей.
 * @property endsAtIso Необязательное время окончания.
 * @property status Lifecycle-статус события.
 * @property salesStatus Статус продаж, который в этом slice пока остается закрытым.
 * @property currency Валюта события в ISO-формате.
 * @property visibility Публичность события.
 * @property hallSnapshot Frozen snapshot схемы зала для последующих event/ticketing шагов.
 * @property priceZones Event-local ценовые зоны.
 * @property pricingAssignments Event-local назначения ценовых зон на snapshot targets.
 * @property availabilityOverrides Event-local overrides доступности snapshot targets.
 */
data class OrganizerEvent(
    val id: String,
    val workspaceId: String,
    val venueId: String,
    val venueName: String,
    val hallSnapshotId: String,
    val sourceTemplateId: String,
    val sourceTemplateName: String,
    val title: String,
    val description: String? = null,
    val startsAtIso: String,
    val doorsOpenAtIso: String? = null,
    val endsAtIso: String? = null,
    val status: EventStatus,
    val salesStatus: EventSalesStatus,
    val currency: String,
    val visibility: EventVisibility,
    val hallSnapshot: EventHallSnapshot,
    val priceZones: List<EventPriceZone> = emptyList(),
    val pricingAssignments: List<EventPricingAssignment> = emptyList(),
    val availabilityOverrides: List<EventAvailabilityOverride> = emptyList(),
)

/**
 * Черновик organizer event до сохранения на backend.
 *
 * @property workspaceId Workspace-владелец события.
 * @property venueId Идентификатор выбранной площадки.
 * @property hallTemplateId Идентификатор hall template, из которого нужно создать snapshot.
 * @property title Название события.
 * @property description Необязательное описание события.
 * @property startsAtIso RFC3339 timestamp начала события.
 * @property doorsOpenAtIso Необязательное время открытия дверей.
 * @property endsAtIso Необязательное время окончания.
 * @property currency Валюта события в ISO-формате.
 * @property visibility Публичность события.
 */
data class EventDraft(
    val workspaceId: String,
    val venueId: String,
    val hallTemplateId: String,
    val title: String,
    val description: String? = null,
    val startsAtIso: String,
    val doorsOpenAtIso: String? = null,
    val endsAtIso: String? = null,
    val currency: String = "RUB",
    val visibility: EventVisibility = EventVisibility.PUBLIC,
)

/**
 * Frozen snapshot схемы зала, который уже принадлежит конкретному событию.
 *
 * @property id Идентификатор snapshot.
 * @property eventId Идентификатор события-владельца.
 * @property sourceTemplateId Идентификатор исходного venue template.
 * @property layout Каноническая frozen-схема, независимая от будущих правок template.
 */
data class EventHallSnapshot(
    val id: String,
    val eventId: String,
    val sourceTemplateId: String,
    val layout: HallLayout,
)

/**
 * Lifecycle-статусы organizer event.
 *
 * @property wireName Значение для API/persistence.
 */
enum class EventStatus(
    val wireName: String,
) {
    DRAFT("draft"),
    PUBLISHED("published"),
    CANCELED("canceled"),
    ;

    companion object {
        /** Восстанавливает статус события по wire-значению. */
        fun fromWireName(value: String): EventStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Статусы продаж organizer event.
 *
 * Этот slice пока работает только с закрытым состоянием, но enum заранее фиксирует будущие
 * согласованные wire-значения lifecycle-а продаж.
 *
 * @property wireName Значение для API/persistence.
 */
enum class EventSalesStatus(
    val wireName: String,
) {
    CLOSED("closed"),
    OPEN("open"),
    PAUSED("paused"),
    SOLD_OUT("sold_out"),
    ;

    companion object {
        /** Восстанавливает sales-статус по wire-значению. */
        fun fromWireName(value: String): EventSalesStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Публичность organizer event.
 *
 * @property wireName Значение для API/persistence.
 */
enum class EventVisibility(
    val wireName: String,
) {
    PUBLIC("public"),
    PRIVATE("private"),
    ;

    companion object {
        /** Восстанавливает публичность события по wire-значению. */
        fun fromWireName(value: String): EventVisibility? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
