package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventUpdateDraft

/**
 * Пользовательские intents organizer event feature.
 */
sealed interface EventIntent {
    /** Полная перезагрузка event context и reference venues. */
    data object LoadContext : EventIntent

    /** Создание нового organizer event draft. */
    data class CreateEvent(
        val draft: EventDraft,
    ) : EventIntent

    /** Полное обновление organizer event details и event-local overrides. */
    data class UpdateEvent(
        val eventId: String,
        val draft: EventUpdateDraft,
    ) : EventIntent

    /** Публикация существующего draft-события. */
    data class PublishEvent(
        val eventId: String,
    ) : EventIntent

    /** Очистка текущей ошибки event feature. */
    data object ClearError : EventIntent
}
