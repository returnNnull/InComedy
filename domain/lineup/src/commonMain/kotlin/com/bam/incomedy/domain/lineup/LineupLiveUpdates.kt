package com.bam.incomedy.domain.lineup

/**
 * Realtime envelope public live-event channel-а конкретного события.
 *
 * @property type Тип backend-события.
 * @property eventId Идентификатор события.
 * @property occurredAtIso ISO-время формирования payload.
 * @property reason Безопасная причина публикации для дедупликации и диагностики.
 * @property summary Audience-safe summary текущего lineup/live-stage состояния.
 */
data class LineupLiveUpdate(
    val type: LineupLiveUpdateType,
    val eventId: String,
    val occurredAtIso: String,
    val reason: String,
    val summary: LineupLiveSummary,
)

/**
 * Поддерживаемые realtime-типы public live-event channel-а.
 *
 * @property wireName Wire-значение backend payload-а.
 */
enum class LineupLiveUpdateType(
    val wireName: String,
) {
    LINEUP_CHANGED("lineup.changed"),
    STAGE_CURRENT_CHANGED("stage.current_changed"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению backend payload-а. */
        fun fromWireName(value: String): LineupLiveUpdateType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Audience-safe summary live stage состояния.
 *
 * @property currentPerformer Текущий комик на сцене.
 * @property nextUp Следующий комик.
 * @property lineup Упрощенный lineup без organizer/application полей.
 */
data class LineupLiveSummary(
    val currentPerformer: LineupLiveEntry? = null,
    val nextUp: LineupLiveEntry? = null,
    val lineup: List<LineupLiveEntry> = emptyList(),
)

/**
 * Audience-safe запись lineup внутри realtime summary.
 *
 * @property id Идентификатор lineup entry.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property orderIndex Позиция внутри lineup.
 * @property status Текущий live-stage статус entry.
 */
data class LineupLiveEntry(
    val id: String,
    val comedianDisplayName: String,
    val orderIndex: Int,
    val status: LineupEntryStatus,
)
