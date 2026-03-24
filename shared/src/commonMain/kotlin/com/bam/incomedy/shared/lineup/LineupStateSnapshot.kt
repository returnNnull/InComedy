package com.bam.incomedy.shared.lineup

/**
 * Export-friendly состояние comedian applications и organizer lineup feature для Swift-слоя.
 *
 * @property selectedEventId Идентификатор события, чьи organizer данные сейчас загружены.
 * @property applications Список organizer-visible заявок.
 * @property lineup Упорядоченный список lineup entries.
 * @property currentPerformerEntryId Идентификатор текущего `on_stage` entry, если он есть.
 * @property currentPerformerDisplayName Имя текущего комика на сцене.
 * @property nextUpEntryId Идентификатор ближайшего `up_next` entry, если он есть.
 * @property nextUpDisplayName Имя комика, отмеченного как следующий.
 * @property isLoading Показывает загрузку organizer context.
 * @property isSubmitting Показывает активную мутацию submit/review/reorder.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class LineupStateSnapshot(
    val selectedEventId: String?,
    val applications: List<ComedianApplicationSnapshot>,
    val lineup: List<LineupEntrySnapshot>,
    val currentPerformerEntryId: String?,
    val currentPerformerDisplayName: String?,
    val nextUpEntryId: String?,
    val nextUpDisplayName: String?,
    val isLoading: Boolean,
    val isSubmitting: Boolean,
    val errorMessage: String?,
)

/**
 * Export-friendly comedian application.
 */
data class ComedianApplicationSnapshot(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String?,
    val statusKey: String,
    val note: String?,
    val reviewedByUserId: String?,
    val reviewedByDisplayName: String?,
    val createdAtIso: String,
    val updatedAtIso: String,
    val statusUpdatedAtIso: String,
)

/**
 * Export-friendly lineup entry.
 */
data class LineupEntrySnapshot(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String?,
    val applicationId: String?,
    val orderIndex: Int,
    val statusKey: String,
    val notes: String?,
    val createdAtIso: String,
    val updatedAtIso: String,
)
