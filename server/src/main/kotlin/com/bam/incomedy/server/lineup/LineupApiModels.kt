package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.db.LineupEntryOrderUpdate
import com.bam.incomedy.server.db.StoredLineupEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON-кодек lineup routes без мягкого игнорирования неизвестных полей. */
internal val lineupJson: Json = Json { ignoreUnknownKeys = false }

/**
 * Один элемент reorder request-а.
 *
 * @property entryId Идентификатор переставляемого lineup entry.
 * @property orderIndex Новая явная позиция.
 */
@Serializable
data class ReorderLineupEntryRequest(
    @SerialName("entry_id")
    val entryId: String,
    @SerialName("order_index")
    val orderIndex: Int,
)

/**
 * Request на organizer reorder всего lineup события.
 *
 * @property entries Полный список lineup entries с новыми позициями.
 */
@Serializable
data class ReorderLineupRequest(
    val entries: List<ReorderLineupEntryRequest>,
)

/**
 * Один lineup entry в HTTP API.
 *
 * @property id Идентификатор записи.
 * @property eventId Идентификатор события.
 * @property comedianUserId Идентификатор комика.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property comedianUsername Необязательный username комика.
 * @property applicationId Необязательная ссылка на исходную approved-заявку.
 * @property orderIndex Явная позиция в lineup.
 * @property status Текущий wire-статус lineup.
 * @property notes Необязательная organizer-заметка.
 * @property createdAtIso ISO-время создания записи.
 * @property updatedAtIso ISO-время последнего изменения.
 */
@Serializable
data class LineupEntryResponse(
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
    companion object {
        /** Собирает lineup response DTO из persistence модели. */
        fun fromStored(entry: StoredLineupEntry): LineupEntryResponse {
            return LineupEntryResponse(
                id = entry.id,
                eventId = entry.eventId,
                comedianUserId = entry.comedianUserId,
                comedianDisplayName = entry.comedianDisplayName,
                comedianUsername = entry.comedianUsername,
                applicationId = entry.applicationId,
                orderIndex = entry.orderIndex,
                status = entry.status.wireName,
                notes = entry.notes,
                createdAtIso = entry.createdAt.toString(),
                updatedAtIso = entry.updatedAt.toString(),
            )
        }
    }
}

/**
 * Response-обертка для полного lineup события.
 *
 * @property entries Упорядоченные записи lineup.
 */
@Serializable
data class LineupListResponse(
    val entries: List<LineupEntryResponse>,
)

/** Преобразует API reorder request в persistence-friendly DTO. */
internal fun ReorderLineupRequest.toOrderUpdates(): List<LineupEntryOrderUpdate> {
    return entries.map { item ->
        LineupEntryOrderUpdate(
            entryId = item.entryId,
            orderIndex = item.orderIndex,
        )
    }
}
