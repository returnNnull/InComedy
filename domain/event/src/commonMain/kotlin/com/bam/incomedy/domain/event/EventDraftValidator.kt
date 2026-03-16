package com.bam.incomedy.domain.event

/**
 * Набор бизнес-валидаций для organizer event drafts.
 *
 * Валидатор живет в domain, чтобы Android/iOS, shared layer и backend routes опирались на единый
 * набор ограничений event foundation slice-а еще до следующего sales/ticketing этапа.
 */
object EventDraftValidator {
    /** Проверяет корректность черновика события и возвращает безопасную ошибку для UI/API. */
    fun validateEventDraft(draft: EventDraft): String? {
        if (draft.workspaceId.isBlank()) return "Не выбран organizer workspace"
        if (draft.venueId.isBlank()) return "Не выбрана площадка события"
        if (draft.hallTemplateId.isBlank()) return "Не выбран шаблон зала"
        if (draft.title.trim().length !in 3..120) return "Название события должно быть от 3 до 120 символов"
        if ((draft.description?.trim()?.length ?: 0) > 2_000) return "Описание события не должно превышать 2000 символов"
        if (draft.startsAtIso.trim().isEmpty()) return "Нужно указать время начала события"
        if (draft.startsAtIso.trim().length !in 10..64) return "Некорректный формат времени начала события"
        if (draft.doorsOpenAtIso?.trim()?.length ?: 0 > 64) return "Некорректный формат времени открытия дверей"
        if (draft.endsAtIso?.trim()?.length ?: 0 > 64) return "Некорректный формат времени окончания"
        val currency = draft.currency.trim()
        if (!currency.matches(Regex("[A-Z]{3}"))) return "Валюта события должна быть в формате ISO-4217"
        return null
    }
}
