package com.bam.incomedy.domain.notifications

/**
 * Audience-safe объявление внутри event feed.
 *
 * @property id Идентификатор announcement.
 * @property eventId Идентификатор события-владельца.
 * @property message Текст объявления, пригодный для показа в audience feed.
 * @property authorRole Безопасная роль источника объявления без раскрытия user identity.
 * @property createdAtIso RFC3339 timestamp публикации.
 */
data class EventAnnouncement(
    val id: String,
    val eventId: String,
    val message: String,
    val authorRole: EventAnnouncementAuthorRole,
    val createdAtIso: String,
)

/**
 * Безопасная audience-facing роль автора announcement-а.
 *
 * @property wireName Стабильное wire/persistence значение роли.
 */
enum class EventAnnouncementAuthorRole(
    val wireName: String,
) {
    ORGANIZER("organizer"),
    HOST("host"),
    SYSTEM("system"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): EventAnnouncementAuthorRole? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
