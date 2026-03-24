package com.bam.incomedy.feature.lineup

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.LineupEntry

/**
 * Single source of truth для comedian applications и organizer lineup feature.
 *
 * @property selectedEventId Идентификатор события, чьи organizer данные сейчас загружены.
 * @property applications Список organizer-visible заявок события.
 * @property lineup Упорядоченный lineup текущего события.
 * @property isLoading Показывает загрузку organizer context.
 * @property isSubmitting Показывает активную submit/review/reorder операцию.
 * @property errorMessage Безопасная ошибка для platform UI.
 */
data class LineupState(
    val selectedEventId: String? = null,
    val applications: List<ComedianApplication> = emptyList(),
    val lineup: List<LineupEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
