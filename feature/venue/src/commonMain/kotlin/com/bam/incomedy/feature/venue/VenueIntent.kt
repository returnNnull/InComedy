package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.VenueDraft

/**
 * Пользовательские intents venue management feature.
 */
sealed interface VenueIntent {
    /** Запрашивает обновление списка площадок. */
    data object LoadVenues : VenueIntent

    /** Создает новую площадку. */
    data class CreateVenue(
        val draft: VenueDraft,
    ) : VenueIntent

    /** Создает hall template для выбранной площадки. */
    data class CreateHallTemplate(
        val venueId: String,
        val draft: HallTemplateDraft,
    ) : VenueIntent

    /** Обновляет существующий hall template. */
    data class UpdateHallTemplate(
        val templateId: String,
        val draft: HallTemplateDraft,
    ) : VenueIntent

    /** Клонирует существующий hall template. */
    data class CloneHallTemplate(
        val templateId: String,
        val clonedName: String? = null,
    ) : VenueIntent

    /** Очищает верхнеуровневую ошибку feature-состояния. */
    data object ClearError : VenueIntent
}
