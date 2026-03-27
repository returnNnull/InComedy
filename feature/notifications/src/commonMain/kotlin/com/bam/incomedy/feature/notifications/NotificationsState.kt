package com.bam.incomedy.feature.notifications

import com.bam.incomedy.domain.notifications.EventAnnouncement

/**
 * Single source of truth announcement feed surface-а.
 *
 * @property selectedEventId Идентификатор события, чей feed сейчас загружен.
 * @property announcements Текущий audience-safe список announcement-ов выбранного события.
 * @property isLoading Показывает активную загрузку feed-а.
 * @property isSubmitting Показывает публикацию нового announcement-а.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class NotificationsState(
    val selectedEventId: String? = null,
    val announcements: List<EventAnnouncement> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
