package com.bam.incomedy.data.notifications.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент backend API для public event announcement feed.
 *
 * Инкапсулирует transport DTO и route contract, чтобы shared/domain слои работали только
 * с audience-safe announcement моделями bounded context-а `notifications`.
 */
class NotificationBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Загружает публичный announcement feed опубликованного public event-а. */
    suspend fun listPublicEventAnnouncements(
        eventId: String,
    ): Result<List<EventAnnouncement>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/public/events/$eventId/announcements")
            ensureBackendSuccess(response, parser)
            response.body<EventAnnouncementListResponse>().announcements.map(EventAnnouncementResponse::toDomain)
        }
    }

    /** Публикует organizer/host announcement через backend API. */
    suspend fun createEventAnnouncement(
        accessToken: String,
        eventId: String,
        message: String,
    ): Result<EventAnnouncement> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/announcements") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateEventAnnouncementRequest(message = message))
            }
            ensureBackendSuccess(response, parser)
            response.body<EventAnnouncementResponse>().toDomain()
        }
    }
}

/** DTO запроса публикации organizer announcement-а. */
@Serializable
private data class CreateEventAnnouncementRequest(
    val message: String,
)

/** DTO списка audience-safe announcement-ов. */
@Serializable
private data class EventAnnouncementListResponse(
    val announcements: List<EventAnnouncementResponse>,
)

/** DTO одного audience-safe announcement-а. */
@Serializable
private data class EventAnnouncementResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    val message: String,
    @SerialName("author_role")
    val authorRole: String,
    @SerialName("created_at")
    val createdAtIso: String,
) {
    /** Маппит backend payload в доменную announcement-модель. */
    fun toDomain(): EventAnnouncement {
        return EventAnnouncement(
            id = id,
            eventId = eventId,
            message = message,
            authorRole = requireNotNull(EventAnnouncementAuthorRole.fromWireName(authorRole)),
            createdAtIso = createdAtIso,
        )
    }
}
