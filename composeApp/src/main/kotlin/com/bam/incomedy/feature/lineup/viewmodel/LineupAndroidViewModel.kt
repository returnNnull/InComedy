package com.bam.incomedy.feature.lineup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.feature.lineup.LineupState
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер общей модели comedian applications и organizer lineup feature.
 *
 * @property application Android application context.
 */
class LineupAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая lineup feature model из KMP-слоя. */
    private val sharedViewModel = InComedyKoin.getLineupViewModel()

    /** Состояние lineup feature для Compose UI. */
    val state: StateFlow<LineupState> = sharedViewModel.state

    /**
     * Загружает organizer context выбранного события.
     *
     * @param eventId Идентификатор organizer event.
     */
    fun loadOrganizerContext(eventId: String) {
        sharedViewModel.loadOrganizerContext(eventId)
    }

    /**
     * Отправляет comedian application на выбранное событие.
     *
     * @param eventId Идентификатор события.
     * @param note Необязательная заметка к заявке.
     */
    fun submitApplication(
        eventId: String,
        note: String?,
    ) {
        sharedViewModel.submitApplication(
            eventId = eventId,
            note = note,
        )
    }

    /**
     * Меняет review-статус organizer-side заявки.
     *
     * @param eventId Идентификатор события.
     * @param applicationId Идентификатор заявки.
     * @param status Новый review-статус.
     */
    fun updateApplicationStatus(
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ) {
        sharedViewModel.updateApplicationStatus(
            eventId = eventId,
            applicationId = applicationId,
            status = status,
        )
    }

    /**
     * Переставляет lineup по новому явному порядку entry id.
     *
     * @param eventId Идентификатор события.
     * @param orderedEntryIds Полный новый порядок lineup entry id.
     */
    fun reorderLineup(
        eventId: String,
        orderedEntryIds: List<String>,
    ) {
        sharedViewModel.reorderLineup(
            eventId = eventId,
            orderedEntryIds = orderedEntryIds,
        )
    }

    /** Очищает текущую lineup-ошибку. */
    fun clearError() {
        sharedViewModel.clearError()
    }
}
