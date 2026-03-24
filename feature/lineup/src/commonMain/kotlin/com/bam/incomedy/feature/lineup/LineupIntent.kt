package com.bam.incomedy.feature.lineup

import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntryStatus

/**
 * Пользовательские intents comedian applications и lineup feature.
 */
sealed interface LineupIntent {
    /** Загружает organizer context по конкретному событию. */
    data class LoadOrganizerContext(
        val eventId: String,
    ) : LineupIntent

    /** Отправляет comedian application на событие. */
    data class SubmitApplication(
        val eventId: String,
        val note: String? = null,
    ) : LineupIntent

    /** Меняет review-статус organizer-side заявки. */
    data class UpdateApplicationStatus(
        val eventId: String,
        val applicationId: String,
        val status: ComedianApplicationStatus,
    ) : LineupIntent

    /** Переставляет lineup по новому явному порядку entry id. */
    data class ReorderLineup(
        val eventId: String,
        val orderedEntryIds: List<String>,
    ) : LineupIntent

    /** Меняет live-stage статус конкретной записи lineup. */
    data class UpdateLineupEntryStatus(
        val eventId: String,
        val entryId: String,
        val status: LineupEntryStatus,
    ) : LineupIntent

    /** Очищает текущую feature-ошибку. */
    data object ClearError : LineupIntent
}
