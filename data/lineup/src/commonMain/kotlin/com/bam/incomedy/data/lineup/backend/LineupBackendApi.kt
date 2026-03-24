package com.bam.incomedy.data.lineup.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryOrderUpdate
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент backend API для comedian applications и organizer lineup.
 *
 * Клиент изолирует Ktor transport и JSON DTO от shared/domain слоев, чтобы будущий platform UI
 * работал только с доменными моделями bounded context-а `lineup`.
 *
 * @property baseUrl Базовый URL backend API.
 * @property parser JSON-парсер transport-DTO.
 * @property httpClient Настроенный HTTP-клиент backend surface.
 */
class LineupBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Отправляет comedian application на событие. */
    suspend fun submitApplication(
        accessToken: String,
        eventId: String,
        note: String?,
    ): Result<ComedianApplication> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/applications") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(SubmitComedianApplicationRequest(note = note))
            }
            ensureBackendSuccess(response, parser)
            response.body<ComedianApplicationResponse>().toDomain()
        }
    }

    /** Загружает organizer список заявок конкретного события. */
    suspend fun listEventApplications(
        accessToken: String,
        eventId: String,
    ): Result<List<ComedianApplication>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/events/$eventId/applications") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<ComedianApplicationListResponse>().applications.map(ComedianApplicationResponse::toDomain)
        }
    }

    /** Меняет review-статус заявки. */
    suspend fun updateApplicationStatus(
        accessToken: String,
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ): Result<ComedianApplication> {
        return runCatching {
            val response = httpClient.patch("$baseUrl/api/v1/events/$eventId/applications/$applicationId") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateComedianApplicationStatusRequest(status = status.wireName))
            }
            ensureBackendSuccess(response, parser)
            response.body<ComedianApplicationResponse>().toDomain()
        }
    }

    /** Загружает lineup конкретного события. */
    suspend fun listEventLineup(
        accessToken: String,
        eventId: String,
    ): Result<List<LineupEntry>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/events/$eventId/lineup") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<LineupListResponse>().entries.map(LineupEntryResponse::toDomain)
        }
    }

    /** Выполняет organizer reorder полного lineup набора. */
    suspend fun reorderLineup(
        accessToken: String,
        eventId: String,
        entries: List<LineupEntryOrderUpdate>,
    ): Result<List<LineupEntry>> {
        return runCatching {
            val response = httpClient.patch("$baseUrl/api/v1/events/$eventId/lineup") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    ReorderLineupRequest(
                        entries = entries.map(ReorderLineupEntryRequest::fromDomain),
                    ),
                )
            }
            ensureBackendSuccess(response, parser)
            response.body<LineupListResponse>().entries.map(LineupEntryResponse::toDomain)
        }
    }

    /** Меняет live-stage статус конкретной записи lineup через backend API. */
    suspend fun updateLineupEntryStatus(
        accessToken: String,
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ): Result<List<LineupEntry>> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/lineup/live-state") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    UpdateLineupLiveStateRequest(
                        entryId = entryId,
                        status = status.wireName,
                    ),
                )
            }
            ensureBackendSuccess(response, parser)
            response.body<LineupListResponse>().entries.map(LineupEntryResponse::toDomain)
        }
    }
}

/** DTO списка organizer applications. */
@Serializable
private data class ComedianApplicationListResponse(
    val applications: List<ComedianApplicationResponse>,
)

/** DTO списка lineup entries. */
@Serializable
private data class LineupListResponse(
    val entries: List<LineupEntryResponse>,
)

/** DTO submit request-а для comedian application. */
@Serializable
private data class SubmitComedianApplicationRequest(
    val note: String? = null,
)

/** DTO request-а на смену review-статуса. */
@Serializable
private data class UpdateComedianApplicationStatusRequest(
    val status: String,
)

/** DTO request-а на смену live-stage статуса lineup entry. */
@Serializable
private data class UpdateLineupLiveStateRequest(
    @SerialName("entry_id")
    val entryId: String,
    val status: String,
)

/** DTO одного comedian application. */
@Serializable
private data class ComedianApplicationResponse(
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
    /** Маппит transport DTO в доменную модель заявки. */
    fun toDomain(): ComedianApplication {
        return ComedianApplication(
            id = id,
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianDisplayName,
            comedianUsername = comedianUsername,
            status = requireNotNull(ComedianApplicationStatus.fromWireName(status)),
            note = note,
            reviewedByUserId = reviewedByUserId,
            reviewedByDisplayName = reviewedByDisplayName,
            createdAtIso = createdAtIso,
            updatedAtIso = updatedAtIso,
            statusUpdatedAtIso = statusUpdatedAtIso,
        )
    }
}

/** DTO одного lineup entry. */
@Serializable
private data class LineupEntryResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("comedian_user_id")
    val comedianUserId: String,
    @SerialName("comedian_display_name")
    val comedianDisplayName: String,
    @SerialName("comedian_username")
    val comedianUsername: String? = null,
    @SerialName("application_id")
    val applicationId: String? = null,
    @SerialName("order_index")
    val orderIndex: Int,
    val status: String,
    val notes: String? = null,
    @SerialName("created_at")
    val createdAtIso: String,
    @SerialName("updated_at")
    val updatedAtIso: String,
) {
    /** Маппит transport DTO в доменную модель lineup entry. */
    fun toDomain(): LineupEntry {
        return LineupEntry(
            id = id,
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianDisplayName,
            comedianUsername = comedianUsername,
            applicationId = applicationId,
            orderIndex = orderIndex,
            status = requireNotNull(LineupEntryStatus.fromWireName(status)),
            notes = notes,
            createdAtIso = createdAtIso,
            updatedAtIso = updatedAtIso,
        )
    }
}

/** DTO reorder request-а для полного lineup набора. */
@Serializable
private data class ReorderLineupRequest(
    val entries: List<ReorderLineupEntryRequest>,
)

/** DTO одной reorder-команды. */
@Serializable
private data class ReorderLineupEntryRequest(
    @SerialName("entry_id")
    val entryId: String,
    @SerialName("order_index")
    val orderIndex: Int,
) {
    companion object {
        /** Собирает transport DTO из доменной reorder-команды. */
        fun fromDomain(update: LineupEntryOrderUpdate): ReorderLineupEntryRequest {
            return ReorderLineupEntryRequest(
                entryId = update.entryId,
                orderIndex = update.orderIndex,
            )
        }
    }
}
