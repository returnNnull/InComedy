package com.bam.incomedy.feature.notifications.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.feature.notifications.NotificationsState
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер общей модели announcement feed и publish surface.
 */
class NotificationsAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val sharedViewModel = InComedyKoin.getNotificationsViewModel()
    val state: StateFlow<NotificationsState> = sharedViewModel.state

    fun loadAnnouncements(eventId: String) {
        sharedViewModel.loadEventFeed(eventId)
    }

    fun createAnnouncement(
        eventId: String,
        message: String,
    ) {
        sharedViewModel.createEventAnnouncement(
            eventId = eventId,
            message = message,
        )
    }

    fun clearError() {
        sharedViewModel.clearError()
    }
}
