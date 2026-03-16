package com.bam.incomedy.server.db

import java.time.OffsetDateTime

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
 * @property hallSnapshot Frozen snapshot схемы зала.
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
    val hallSnapshot: StoredEventHallSnapshot,
)

/**
 * Persistence-контракт organizer event management bounded context-а.
 */
interface EventRepository {
    /** Возвращает события, доступные пользователю по active workspace memberships. */
    fun listEvents(userId: String): List<StoredOrganizerEvent>

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

    /** Переводит событие в published-состояние и возвращает обновленную запись. */
    fun publishEvent(eventId: String): StoredOrganizerEvent?
}
