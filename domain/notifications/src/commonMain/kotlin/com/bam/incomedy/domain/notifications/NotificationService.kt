package com.bam.incomedy.domain.notifications

/**
 * Контракт provider-agnostic notification foundation для event announcements.
 */
interface NotificationService {
    /** Возвращает публичный feed объявлений конкретного опубликованного события. */
    suspend fun listPublicEventAnnouncements(
        eventId: String,
    ): Result<List<EventAnnouncement>>

    /** Публикует organizer/host announcement в feed конкретного события. */
    suspend fun createEventAnnouncement(
        accessToken: String,
        eventId: String,
        message: String,
    ): Result<EventAnnouncement>
}
