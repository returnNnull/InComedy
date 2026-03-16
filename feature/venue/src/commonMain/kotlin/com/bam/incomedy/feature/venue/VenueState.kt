package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.OrganizerVenue

/**
 * Единое UI-состояние organizer venue management feature.
 *
 * @property venues Список площадок, доступных текущей сессии.
 * @property isLoading Показывает, что сейчас обновляется список площадок.
 * @property isSubmitting Показывает, что сейчас выполняется create/update/clone mutation.
 * @property errorMessage Безопасная ошибка верхнего уровня.
 */
data class VenueState(
    val venues: List<OrganizerVenue> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
