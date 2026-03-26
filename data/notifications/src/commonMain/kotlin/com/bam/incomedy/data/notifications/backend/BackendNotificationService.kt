package com.bam.incomedy.data.notifications.backend

import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.NotificationService

/**
 * Backend-адаптер provider-agnostic announcement service contract-а.
 */
class BackendNotificationService(
    private val notificationBackendApi: NotificationBackendApi,
) : NotificationService {
    override suspend fun listPublicEventAnnouncements(
        eventId: String,
    ): Result<List<EventAnnouncement>> {
        return notificationBackendApi.listPublicEventAnnouncements(eventId = eventId)
    }

    override suspend fun createEventAnnouncement(
        accessToken: String,
        eventId: String,
        message: String,
    ): Result<EventAnnouncement> {
        return notificationBackendApi.createEventAnnouncement(
            accessToken = accessToken,
            eventId = eventId,
            message = message,
        )
    }
}
