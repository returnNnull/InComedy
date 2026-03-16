package com.bam.incomedy.data.event.backend

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventManagementService
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.domain.event.OrganizerEvent

/**
 * Backend-адаптер domain-контракта organizer event management.
 *
 * @property eventBackendApi HTTP-клиент organizer event surface.
 */
class BackendEventManagementService(
    private val eventBackendApi: EventBackendApi,
) : EventManagementService {
    /** Загружает список событий текущей organizer-сессии через backend API. */
    override suspend fun listEvents(accessToken: String): Result<List<OrganizerEvent>> {
        return eventBackendApi.listEvents(accessToken = accessToken)
    }

    /** Загружает одно organizer event detail через backend API. */
    override suspend fun getEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent> {
        return eventBackendApi.getEvent(
            accessToken = accessToken,
            eventId = eventId,
        )
    }

    /** Создает draft-событие через backend API. */
    override suspend fun createEvent(
        accessToken: String,
        draft: EventDraft,
    ): Result<OrganizerEvent> {
        return eventBackendApi.createEvent(
            accessToken = accessToken,
            draft = draft,
        )
    }

    /** Обновляет organizer event details и event-local overrides через backend API. */
    override suspend fun updateEvent(
        accessToken: String,
        eventId: String,
        draft: EventUpdateDraft,
    ): Result<OrganizerEvent> {
        return eventBackendApi.updateEvent(
            accessToken = accessToken,
            eventId = eventId,
            draft = draft,
        )
    }

    /** Публикует draft-событие через backend API. */
    override suspend fun publishEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent> {
        return eventBackendApi.publishEvent(
            accessToken = accessToken,
            eventId = eventId,
        )
    }
}
