package com.bam.incomedy.shared.lineup

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.feature.lineup.LineupState
import com.bam.incomedy.feature.lineup.LineupViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей lineup feature model для Swift-слоя.
 *
 * Bridge скрывает typed Kotlin state и дает iOS стабильные snapshot-модели и высокоуровневые
 * команды для comedian submit, organizer review и lineup reorder.
 *
 * @property viewModel Общая feature model bounded context-а `lineup`.
 */
class LineupBridge(
    private val viewModel: LineupViewModel = InComedyKoin.getLineupViewModel(),
) : BaseFeatureBridge() {
    /** Возвращает текущее состояние lineup feature единым снимком. */
    fun currentState(): LineupStateSnapshot = viewModel.state.value.toSnapshot()

    /** Подписывает Swift-слой на обновления lineup state. */
    fun observeState(onState: (LineupStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    /** Загружает organizer applications и lineup для выбранного события. */
    fun loadOrganizerContext(eventId: String) {
        viewModel.loadOrganizerContext(eventId)
    }

    /** Отправляет comedian application на выбранное событие. */
    fun submitApplication(
        eventId: String,
        note: String?,
    ) {
        viewModel.submitApplication(
            eventId = eventId,
            note = note,
        )
    }

    /** Меняет review-статус organizer-side заявки. */
    fun updateApplicationStatus(
        eventId: String,
        applicationId: String,
        statusKey: String,
    ) {
        val status = ComedianApplicationStatus.fromWireName(statusKey) ?: return
        viewModel.updateApplicationStatus(
            eventId = eventId,
            applicationId = applicationId,
            status = status,
        )
    }

    /** Переставляет lineup по новому явному порядку entry id. */
    fun reorderLineup(
        eventId: String,
        orderedEntryIds: List<String>,
    ) {
        viewModel.reorderLineup(
            eventId = eventId,
            orderedEntryIds = orderedEntryIds,
        )
    }

    /** Очищает текущую ошибку feature-а. */
    fun clearError() {
        viewModel.clearError()
    }
}

/** Преобразует внутреннее состояние lineup feature в export-friendly snapshot. */
private fun LineupState.toSnapshot(): LineupStateSnapshot {
    return LineupStateSnapshot(
        selectedEventId = selectedEventId,
        applications = applications.map(ComedianApplication::toSnapshot),
        lineup = lineup.map(LineupEntry::toSnapshot),
        isLoading = isLoading,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
    )
}

/** Преобразует доменную заявку в iOS-friendly snapshot. */
private fun ComedianApplication.toSnapshot(): ComedianApplicationSnapshot {
    return ComedianApplicationSnapshot(
        id = id,
        eventId = eventId,
        comedianUserId = comedianUserId,
        comedianDisplayName = comedianDisplayName,
        comedianUsername = comedianUsername,
        statusKey = status.wireName,
        note = note,
        reviewedByUserId = reviewedByUserId,
        reviewedByDisplayName = reviewedByDisplayName,
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
        statusUpdatedAtIso = statusUpdatedAtIso,
    )
}

/** Преобразует доменный lineup entry в iOS-friendly snapshot. */
private fun LineupEntry.toSnapshot(): LineupEntrySnapshot {
    return LineupEntrySnapshot(
        id = id,
        eventId = eventId,
        comedianUserId = comedianUserId,
        comedianDisplayName = comedianDisplayName,
        comedianUsername = comedianUsername,
        applicationId = applicationId,
        orderIndex = orderIndex,
        statusKey = status.wireName,
        notes = notes,
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
    )
}
