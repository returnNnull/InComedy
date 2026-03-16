package com.bam.incomedy.domain.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit-тесты доменного валидатора organizer event drafts.
 */
class EventDraftValidatorTest {
    /** Проверяет happy-path черновика события. */
    @Test
    fun validDraftPassesValidation() {
        assertNull(
            EventDraftValidator.validateEventDraft(
                EventDraft(
                    workspaceId = "ws-1",
                    venueId = "venue-1",
                    hallTemplateId = "template-1",
                    title = "Late Night Standup",
                    description = "Тестовый вечер",
                    startsAtIso = "2026-03-20T19:00:00+03:00",
                    doorsOpenAtIso = "2026-03-20T18:30:00+03:00",
                    endsAtIso = "2026-03-20T21:30:00+03:00",
                    currency = "RUB",
                    visibility = EventVisibility.PUBLIC,
                ),
            ),
        )
    }

    /** Проверяет обязательность выбранного hall template. */
    @Test
    fun missingTemplateFailsValidation() {
        assertEquals(
            "Не выбран шаблон зала",
            EventDraftValidator.validateEventDraft(
                EventDraft(
                    workspaceId = "ws-1",
                    venueId = "venue-1",
                    hallTemplateId = "",
                    title = "Late Night Standup",
                    startsAtIso = "2026-03-20T19:00:00+03:00",
                ),
            ),
        )
    }

    /** Проверяет нормализацию валюты к ожидаемому ISO-формату. */
    @Test
    fun invalidCurrencyFailsValidation() {
        assertEquals(
            "Валюта события должна быть в формате ISO-4217",
            EventDraftValidator.validateEventDraft(
                EventDraft(
                    workspaceId = "ws-1",
                    venueId = "venue-1",
                    hallTemplateId = "template-1",
                    title = "Late Night Standup",
                    startsAtIso = "2026-03-20T19:00:00+03:00",
                    currency = "rub",
                ),
            ),
        )
    }
}
