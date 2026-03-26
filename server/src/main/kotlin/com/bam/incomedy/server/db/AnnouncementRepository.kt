package com.bam.incomedy.server.db

import java.time.OffsetDateTime

/**
 * Сохраненное audience-safe announcement-сообщение конкретного события.
 *
 * @property id Идентификатор announcement.
 * @property eventId Идентификатор события-владельца.
 * @property createdByUserId Идентификатор автора внутри платформы.
 * @property authorRole Безопасная роль источника (`organizer`, `host`, `system`).
 * @property message Текст объявления.
 * @property createdAt Время публикации.
 */
data class StoredEventAnnouncement(
    val id: String,
    val eventId: String,
    val createdByUserId: String,
    val authorRole: String,
    val message: String,
    val createdAt: OffsetDateTime,
)

/**
 * Persistence-контракт bounded context-а event announcements/feed.
 */
interface AnnouncementRepository {
    /** Возвращает все announcement-ы события в порядке от новых к старым. */
    fun listEventAnnouncements(eventId: String): List<StoredEventAnnouncement>

    /** Создает новое announcement-сообщение внутри feed конкретного события. */
    fun createEventAnnouncement(
        eventId: String,
        createdByUserId: String,
        authorRole: String,
        message: String,
    ): StoredEventAnnouncement
}
