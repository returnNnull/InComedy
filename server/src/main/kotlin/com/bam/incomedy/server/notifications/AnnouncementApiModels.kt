package com.bam.incomedy.server.notifications

import com.bam.incomedy.domain.notifications.EventAnnouncement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON-кодек request/response моделей announcement surface-а. */
internal val notificationsJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

/**
 * Request публикации нового organizer announcement-а.
 *
 * @property message Текст объявления для audience feed-а.
 */
@Serializable
data class CreateEventAnnouncementRequest(
    val message: String,
)

/**
 * Response одного audience-safe announcement-а.
 *
 * @property id Идентификатор announcement.
 * @property eventId Идентификатор события.
 * @property message Текст объявления.
 * @property authorRole Безопасная роль автора без раскрытия user identity.
 * @property createdAtIso RFC3339 timestamp публикации.
 */
@Serializable
data class EventAnnouncementResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    val message: String,
    @SerialName("author_role")
    val authorRole: String,
    @SerialName("created_at")
    val createdAtIso: String,
) {
    companion object {
        /** Собирает transport response из доменной модели announcement-а. */
        fun fromDomain(announcement: EventAnnouncement): EventAnnouncementResponse {
            return EventAnnouncementResponse(
                id = announcement.id,
                eventId = announcement.eventId,
                message = announcement.message,
                authorRole = announcement.authorRole.wireName,
                createdAtIso = announcement.createdAtIso,
            )
        }
    }
}

/**
 * Response списка audience-safe announcement-ов одного события.
 *
 * @property announcements Упорядоченные feed entries.
 */
@Serializable
data class EventAnnouncementListResponse(
    val announcements: List<EventAnnouncementResponse>,
)
