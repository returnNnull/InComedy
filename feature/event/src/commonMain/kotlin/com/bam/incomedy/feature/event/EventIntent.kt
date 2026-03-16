package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft

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

    /** Публикация существующего draft-события. */
    data class PublishEvent(
        val eventId: String,
    ) : EventIntent

    /** Очистка текущей ошибки event feature. */
    data object ClearError : EventIntent
}
