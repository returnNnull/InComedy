package com.bam.incomedy.shared.notifications

import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.feature.notifications.NotificationsState
import com.bam.incomedy.feature.notifications.NotificationsViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей notifications feature model для Swift-слоя.
 */
class NotificationsBridge(
    private val viewModel: NotificationsViewModel = InComedyKoin.getNotificationsViewModel(),
) : BaseFeatureBridge() {
    fun currentState(): NotificationsStateSnapshot = viewModel.state.value.toSnapshot()

    fun observeState(onState: (NotificationsStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    fun loadAnnouncements(eventId: String) {
        viewModel.loadEventFeed(eventId)
    }

    fun createAnnouncement(
        eventId: String,
        message: String,
    ) {
        viewModel.createEventAnnouncement(
            eventId = eventId,
            message = message,
        )
    }

    fun clearError() {
        viewModel.clearError()
    }
}

private fun NotificationsState.toSnapshot(): NotificationsStateSnapshot {
    return NotificationsStateSnapshot(
        selectedEventId = selectedEventId,
        announcements = announcements.map(EventAnnouncement::toSnapshot),
        isLoading = isLoading,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
    )
}

private fun EventAnnouncement.toSnapshot(): EventAnnouncementSnapshot {
    return EventAnnouncementSnapshot(
        id = id,
        eventId = eventId,
        message = message,
        authorRoleKey = authorRole.wireName,
        createdAtIso = createdAtIso,
    )
}
