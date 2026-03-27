package com.bam.incomedy.feature.notifications

import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.NotificationService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared-координатор public announcement feed-а и organizer/host publish surface-а.
 *
 * Модель использует уже доставленный provider-agnostic `NotificationService` и намеренно
 * ограничивается только public event feed + protected create route без `/api/v1/me/notifications`
 * и без выбора push provider.
 *
 * @property notificationService Domain-сервис announcements/feed bounded context-а.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property dispatcher Dispatcher фоновых операций.
 */
class NotificationsViewModel(
    private val notificationService: NotificationService,
    private val accessTokenProvider: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    /** Загружает public announcement feed выбранного опубликованного public event-а. */
    fun loadEventFeed(eventId: String) {
        val normalizedEventId = eventId.trim()
        if (normalizedEventId.isEmpty()) {
            _state.update { it.copy(errorMessage = "Не выбран event для загрузки announcement feed") }
            return
        }

        scope.launch {
            _state.update {
                it.copy(
                    selectedEventId = normalizedEventId,
                    isLoading = true,
                    errorMessage = null,
                )
            }
            notificationService.listPublicEventAnnouncements(normalizedEventId).fold(
                onSuccess = { announcements ->
                    _state.update {
                        it.copy(
                            selectedEventId = normalizedEventId,
                            announcements = sortAnnouncements(announcements),
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            selectedEventId = normalizedEventId,
                            isLoading = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось загрузить announcement feed",
                        )
                    }
                },
            )
        }
    }

    /** Публикует organizer/host announcement и обновляет текущий локальный feed. */
    fun createEventAnnouncement(
        eventId: String,
        message: String,
    ) {
        val normalizedEventId = eventId.trim()
        val normalizedMessage = message.trim()
        if (normalizedEventId.isEmpty()) {
            _state.update { it.copy(errorMessage = "Не выбран event для публикации announcement-а") }
            return
        }
        if (normalizedMessage.isEmpty()) {
            _state.update { it.copy(errorMessage = "Текст announcement-а не должен быть пустым") }
            return
        }
        if (normalizedMessage.length > 1000) {
            _state.update { it.copy(errorMessage = "Announcement message должен быть не длиннее 1000 символов") }
            return
        }

        val accessToken = requireAccessToken() ?: return
        scope.launch {
            _state.update {
                it.copy(
                    selectedEventId = normalizedEventId,
                    isSubmitting = true,
                    errorMessage = null,
                )
            }
            notificationService.createEventAnnouncement(
                accessToken = accessToken,
                eventId = normalizedEventId,
                message = normalizedMessage,
            ).fold(
                onSuccess = { announcement ->
                    _state.update {
                        val mergedAnnouncements = when (it.selectedEventId) {
                            normalizedEventId -> sortAnnouncements(
                                (it.announcements + announcement).distinctBy(EventAnnouncement::id),
                            )

                            else -> listOf(announcement)
                        }
                        it.copy(
                            selectedEventId = normalizedEventId,
                            announcements = mergedAnnouncements,
                            isSubmitting = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            selectedEventId = normalizedEventId,
                            isSubmitting = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось опубликовать announcement",
                        )
                    }
                },
            )
        }
    }

    /** Скрывает текущую notifications-ошибку. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun requireAccessToken(): String? {
        val accessToken = accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (accessToken == null) {
            _state.update {
                it.copy(errorMessage = "Нет активной сессии для публикации announcement-а")
            }
        }
        return accessToken
    }

    private fun sortAnnouncements(
        announcements: List<EventAnnouncement>,
    ): List<EventAnnouncement> {
        return announcements.sortedWith(
            compareByDescending<EventAnnouncement> { it.createdAtIso }
                .thenByDescending { it.id },
        )
    }
}
