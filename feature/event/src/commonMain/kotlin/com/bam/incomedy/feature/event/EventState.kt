package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.venue.OrganizerVenue

/**
 * Single source of truth organizer event feature.
 *
 * @property events Список событий, доступных текущей organizer-сессии.
 * @property venues Доступные площадки и templates как upstream reference data для event form.
 * @property isLoading Показывает загрузку event/venue context.
 * @property isSubmitting Показывает активную event mutation.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class EventState(
    val events: List<OrganizerEvent> = emptyList(),
    val venues: List<OrganizerVenue> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
