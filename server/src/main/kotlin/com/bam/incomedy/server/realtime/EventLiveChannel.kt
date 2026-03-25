package com.bam.incomedy.server.realtime

import com.bam.incomedy.server.db.LineupEntryStatus
import com.bam.incomedy.server.db.StoredLineupEntry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/** JSON-кодек realtime event channel со стабильной формой payload, включая default-поля. */
internal val eventLiveJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

/**
 * In-memory broadcaster audience-safe live event сообщений.
 *
 * Первый delivery шаг `EPIC-069` ограничен текущим backend process-ом: сообщения публикуются и
 * читаются только внутри одного приложения без durable outbox и multi-instance fanout.
 */
class EventLiveChannelBroadcaster(
    private val nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
) {
    /** Hot streams по конкретным event id. */
    private val channels = ConcurrentHashMap<String, MutableSharedFlow<EventLiveEnvelope>>()

    /** Подписывает WebSocket session на live stream конкретного события. */
    fun subscribe(eventId: String): SharedFlow<EventLiveEnvelope> {
        return channelFor(eventId)
    }

    /** Публикует audience-safe изменение lineup. */
    suspend fun publishLineupChanged(
        eventId: String,
        lineup: List<StoredLineupEntry>,
        reason: String,
    ) {
        channelFor(eventId).emit(
            EventLiveEnvelope.lineupChanged(
                eventId = eventId,
                lineup = lineup,
                reason = reason,
                occurredAt = nowProvider(),
            ),
        )
    }

    /** Публикует audience-safe изменение текущего/следующего выступающего. */
    suspend fun publishStageChanged(
        eventId: String,
        lineup: List<StoredLineupEntry>,
        reason: String,
    ) {
        channelFor(eventId).emit(
            EventLiveEnvelope.stageChanged(
                eventId = eventId,
                lineup = lineup,
                reason = reason,
                occurredAt = nowProvider(),
            ),
        )
    }

    /** Возвращает initial snapshot для нового подключения. */
    fun initialSnapshot(
        eventId: String,
        lineup: List<StoredLineupEntry>,
    ): EventLiveEnvelope {
        return EventLiveEnvelope.lineupChanged(
            eventId = eventId,
            lineup = lineup,
            reason = "initial_snapshot",
            occurredAt = nowProvider(),
        )
    }

    /** Получает или создает shared flow для конкретного события. */
    private fun channelFor(eventId: String): MutableSharedFlow<EventLiveEnvelope> {
        return channels.computeIfAbsent(eventId) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = 32,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    }
}

/**
 * Realtime envelope audience-facing event live channel-а.
 *
 * @property type Тип события (`lineup.changed` или `stage.current_changed`).
 * @property eventId Идентификатор события.
 * @property occurredAtIso Момент формирования payload.
 * @property reason Безопасная причина публикации для клиентской дедупликации/отладки.
 * @property summary Audience-safe summary текущего live stage состояния.
 */
@Serializable
data class EventLiveEnvelope(
    val type: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("occurred_at")
    val occurredAtIso: String,
    val reason: String,
    val summary: EventLiveSummary,
) {
    companion object {
        /** Собирает envelope для lineup change. */
        fun lineupChanged(
            eventId: String,
            lineup: List<StoredLineupEntry>,
            reason: String,
            occurredAt: OffsetDateTime,
        ): EventLiveEnvelope {
            return EventLiveEnvelope(
                type = "lineup.changed",
                eventId = eventId,
                occurredAtIso = occurredAt.toString(),
                reason = reason,
                summary = lineup.toAudienceSummary(),
            )
        }

        /** Собирает envelope для current/next change. */
        fun stageChanged(
            eventId: String,
            lineup: List<StoredLineupEntry>,
            reason: String,
            occurredAt: OffsetDateTime,
        ): EventLiveEnvelope {
            return EventLiveEnvelope(
                type = "stage.current_changed",
                eventId = eventId,
                occurredAtIso = occurredAt.toString(),
                reason = reason,
                summary = lineup.toAudienceSummary(),
            )
        }
    }
}

/**
 * Audience-safe summary live stage состояния.
 *
 * @property currentPerformer Текущий комик на сцене.
 * @property nextUp Следующий комик.
 * @property lineup Упрощенный lineup без внутренних organizer/application полей.
 */
@Serializable
data class EventLiveSummary(
    @SerialName("current_performer")
    val currentPerformer: EventLiveLineupEntry? = null,
    @SerialName("next_up")
    val nextUp: EventLiveLineupEntry? = null,
    val lineup: List<EventLiveLineupEntry> = emptyList(),
)

/**
 * Audience-safe запись lineup для live channel-а.
 *
 * @property id Идентификатор lineup entry.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property orderIndex Позиция в lineup.
 * @property status Текущий live-stage статус.
 */
@Serializable
data class EventLiveLineupEntry(
    val id: String,
    @SerialName("comedian_display_name")
    val comedianDisplayName: String,
    @SerialName("order_index")
    val orderIndex: Int,
    val status: String,
)

/** Собирает audience-safe summary из полного organizer lineup. */
private fun List<StoredLineupEntry>.toAudienceSummary(): EventLiveSummary {
    val ordered = sortedWith(compareBy<StoredLineupEntry>({ it.orderIndex }, { it.createdAt }))
    return EventLiveSummary(
        currentPerformer = ordered.firstOrNull { it.status == LineupEntryStatus.ON_STAGE }?.toAudienceEntry(),
        nextUp = ordered.firstOrNull { it.status == LineupEntryStatus.UP_NEXT }?.toAudienceEntry(),
        lineup = ordered.map(StoredLineupEntry::toAudienceEntry),
    )
}

/** Упрощает organizer lineup entry до audience-safe payload-а. */
private fun StoredLineupEntry.toAudienceEntry(): EventLiveLineupEntry {
    return EventLiveLineupEntry(
        id = id,
        comedianDisplayName = comedianDisplayName,
        orderIndex = orderIndex,
        status = status.wireName,
    )
}
