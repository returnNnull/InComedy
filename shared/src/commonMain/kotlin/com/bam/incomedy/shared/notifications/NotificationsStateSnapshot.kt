package com.bam.incomedy.shared.notifications

/**
 * Export-friendly состояние event announcement feed feature для Swift-слоя.
 *
 * @property selectedEventId Идентификатор события, чей feed сейчас загружен.
 * @property announcements Audience-safe announcement entries выбранного события.
 * @property isLoading Показывает загрузку feed-а.
 * @property isSubmitting Показывает публикацию нового announcement-а.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class NotificationsStateSnapshot(
    val selectedEventId: String?,
    val announcements: List<EventAnnouncementSnapshot>,
    val isLoading: Boolean,
    val isSubmitting: Boolean,
    val errorMessage: String?,
)

/**
 * Export-friendly announcement feed entry.
 */
data class EventAnnouncementSnapshot(
    val id: String,
    val eventId: String,
    val message: String,
    val authorRoleKey: String,
    val createdAtIso: String,
)
