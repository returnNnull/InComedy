package com.bam.incomedy.server.db

import java.time.OffsetDateTime

/**
 * Сохраненная event-local ценовая зона.
 *
 * @property id Стабильный идентификатор зоны внутри события.
 * @property name Отображаемое название зоны.
 * @property priceMinor Цена в minor units.
 * @property currency Валюта зоны.
 * @property salesStartAt Необязательное время начала продаж для зоны.
 * @property salesEndAt Необязательное время конца продаж для зоны.
 * @property sourceTemplatePriceZoneId Необязательная ссылка на исходную template price zone.
 */
data class StoredEventPriceZone(
    val id: String,
    val name: String,
    val priceMinor: Int,
    val currency: String,
    val salesStartAt: OffsetDateTime? = null,
    val salesEndAt: OffsetDateTime? = null,
    val sourceTemplatePriceZoneId: String? = null,
)

/**
 * Сохраненное назначение event-local цены на snapshot target.
 *
 * @property targetType Тип snapshot target-а.
 * @property targetRef Идентификатор конкретного target-а.
 * @property eventPriceZoneId Идентификатор event-local ценовой зоны.
 */
data class StoredEventPricingAssignment(
    val targetType: String,
    val targetRef: String,
    val eventPriceZoneId: String,
)

/**
 * Сохраненный event-local override доступности.
 *
 * @property targetType Тип snapshot target-а.
 * @property targetRef Идентификатор конкретного target-а.
 * @property availabilityStatus Event-local состояние доступности.
 */
data class StoredEventAvailabilityOverride(
    val targetType: String,
    val targetRef: String,
    val availabilityStatus: String,
)

/**
 * Frozen snapshot схемы зала, уже привязанный к конкретному organizer event.
 *
 * @property id Идентификатор snapshot.
 * @property eventId Идентификатор события-владельца.
 * @property sourceTemplateId Идентификатор исходного hall template.
 * @property sourceTemplateName Имя исходного hall template на момент заморозки.
 * @property snapshotJson Канонический JSON layout snapshot.
 */
data class StoredEventHallSnapshot(
    val id: String,
    val eventId: String,
    val sourceTemplateId: String,
    val sourceTemplateName: String,
    val snapshotJson: String,
)

/**
 * Сохраненное organizer event bounded context-а.
 *
 * @property id Идентификатор события.
 * @property workspaceId Workspace-владелец события.
 * @property venueId Идентификатор площадки, выбранной при создании.
 * @property venueName Frozen имя площадки на момент создания события.
 * @property title Название события.
 * @property description Необязательное описание события.
 * @property startsAt Плановое время начала.
 * @property doorsOpenAt Необязательное время открытия дверей.
 * @property endsAt Необязательное время завершения.
 * @property status Lifecycle-статус события.
 * @property salesStatus Lifecycle-статус продаж.
 * @property currency Валюта события.
 * @property visibility Публичность события.
 * @property updatedAt Момент последнего изменения organizer-конфигурации или lifecycle события.
 * @property hallSnapshot Frozen snapshot схемы зала.
 * @property priceZones Event-local ценовые зоны.
 * @property pricingAssignments Event-local назначения цен на snapshot targets.
 * @property availabilityOverrides Event-local overrides доступности snapshot targets.
 */
data class StoredOrganizerEvent(
    val id: String,
    val workspaceId: String,
    val venueId: String,
    val venueName: String,
    val title: String,
    val description: String? = null,
    val startsAt: OffsetDateTime,
    val doorsOpenAt: OffsetDateTime? = null,
    val endsAt: OffsetDateTime? = null,
    val status: String,
    val salesStatus: String,
    val currency: String,
    val visibility: String,
    val updatedAt: OffsetDateTime,
    val hallSnapshot: StoredEventHallSnapshot,
    val priceZones: List<StoredEventPriceZone> = emptyList(),
    val pricingAssignments: List<StoredEventPricingAssignment> = emptyList(),
    val availabilityOverrides: List<StoredEventAvailabilityOverride> = emptyList(),
)

/**
 * Persistence-контракт organizer event management bounded context-а.
 */
interface EventRepository {
    /** Возвращает события, доступные пользователю по active workspace memberships. */
    fun listEvents(userId: String): List<StoredOrganizerEvent>

    /** Возвращает опубликованные public-события для audience discovery surface. */
    fun listPublicEvents(): List<StoredOrganizerEvent>

    /** Создает draft-событие и сразу сохраняет frozen hall snapshot. */
    fun createEvent(
        workspaceId: String,
        venueId: String,
        venueName: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        status: String,
        salesStatus: String,
        currency: String,
        visibility: String,
        sourceTemplateId: String,
        sourceTemplateName: String,
        snapshotJson: String,
    ): StoredOrganizerEvent

    /** Возвращает событие по его id вместе с frozen snapshot. */
    fun findEvent(eventId: String): StoredOrganizerEvent?

    /** Обновляет event-local organizer configuration поверх frozen snapshot. */
    fun updateEvent(
        eventId: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        currency: String,
        visibility: String,
        priceZones: List<StoredEventPriceZone>,
        pricingAssignments: List<StoredEventPricingAssignment>,
        availabilityOverrides: List<StoredEventAvailabilityOverride>,
    ): StoredOrganizerEvent?

    /** Переводит событие в published-состояние и возвращает обновленную запись. */
    fun publishEvent(eventId: String): StoredOrganizerEvent?

    /** Открывает продажи для опубликованного события. */
    fun openEventSales(eventId: String): StoredOrganizerEvent?

    /** Ставит продажи события на паузу. */
    fun pauseEventSales(eventId: String): StoredOrganizerEvent?

    /** Отменяет событие и закрывает его продажи. */
    fun cancelEvent(eventId: String): StoredOrganizerEvent?
}
