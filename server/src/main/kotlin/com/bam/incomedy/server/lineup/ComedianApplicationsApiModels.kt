package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.db.StoredComedianApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON-кодек для request body comedian applications routes без лишней терпимости к схеме. */
internal val comedianApplicationsJson: Json = Json { ignoreUnknownKeys = false }

/**
 * Request подачи заявки комиком на событие.
 *
 * @property note Необязательный комментарий/заметка к заявке.
 */
@Serializable
data class SubmitComedianApplicationRequest(
    val note: String? = null,
)

/**
 * Request смены review-статуса organizer-ом.
 *
 * @property status Новый статус заявки.
 */
@Serializable
data class UpdateComedianApplicationStatusRequest(
    val status: String,
)

/**
 * Ответ с одной заявкой комика.
 *
 * @property id Идентификатор заявки.
 * @property eventId Идентификатор события.
 * @property comedianUserId Идентификатор комика.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property comedianUsername Необязательный username.
 * @property status Текущий статус заявки.
 * @property note Необязательная заметка комика.
 * @property reviewedByUserId Необязательный reviewer id.
 * @property reviewedByDisplayName Необязательное имя reviewer-а.
 * @property createdAtIso ISO-время создания.
 * @property updatedAtIso ISO-время последнего обновления записи.
 * @property statusUpdatedAtIso ISO-время последней смены статуса.
 */
@Serializable
data class ComedianApplicationResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("comedian_user_id")
    val comedianUserId: String,
    @SerialName("comedian_display_name")
    val comedianDisplayName: String,
    @SerialName("comedian_username")
    val comedianUsername: String? = null,
    val status: String,
    val note: String? = null,
    @SerialName("reviewed_by_user_id")
    val reviewedByUserId: String? = null,
    @SerialName("reviewed_by_display_name")
    val reviewedByDisplayName: String? = null,
    @SerialName("created_at")
    val createdAtIso: String,
    @SerialName("updated_at")
    val updatedAtIso: String,
    @SerialName("status_updated_at")
    val statusUpdatedAtIso: String,
) {
    companion object {
        /** Собирает HTTP-response DTO из persistence модели заявки. */
        fun fromStored(application: StoredComedianApplication): ComedianApplicationResponse {
            return ComedianApplicationResponse(
                id = application.id,
                eventId = application.eventId,
                comedianUserId = application.comedianUserId,
                comedianDisplayName = application.comedianDisplayName,
                comedianUsername = application.comedianUsername,
                status = application.status.wireName,
                note = application.note,
                reviewedByUserId = application.reviewedByUserId,
                reviewedByDisplayName = application.reviewedByDisplayName,
                createdAtIso = application.createdAt.toString(),
                updatedAtIso = application.updatedAt.toString(),
                statusUpdatedAtIso = application.statusUpdatedAt.toString(),
            )
        }
    }
}

/**
 * Response-обертка списка заявок конкретного события.
 *
 * @property applications Упорядоченный organizer список заявок.
 */
@Serializable
data class ComedianApplicationListResponse(
    val applications: List<ComedianApplicationResponse>,
)
