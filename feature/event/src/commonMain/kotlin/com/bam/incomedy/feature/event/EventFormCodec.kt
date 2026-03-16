package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventVisibility

/**
 * Общий parser organizer event forms.
 *
 * Codec централизует нормализацию строковых полей для Android и iOS, чтобы event feature не
 * расходилась между платформами по trim/currency/visibility правилам.
 */
object EventFormCodec {
    /**
     * Собирает доменный draft события из platform-friendly form values.
     */
    fun toEventDraft(
        workspaceId: String,
        venueId: String,
        hallTemplateId: String,
        title: String,
        description: String?,
        startsAtIso: String,
        doorsOpenAtIso: String?,
        endsAtIso: String?,
        currency: String,
        visibilityKey: String,
    ): Result<EventDraft> {
        return runCatching {
            EventDraft(
                workspaceId = workspaceId.trim(),
                venueId = venueId.trim(),
                hallTemplateId = hallTemplateId.trim(),
                title = title.trim(),
                description = description?.trim()?.takeIf(String::isNotBlank),
                startsAtIso = startsAtIso.trim(),
                doorsOpenAtIso = doorsOpenAtIso?.trim()?.takeIf(String::isNotBlank),
                endsAtIso = endsAtIso?.trim()?.takeIf(String::isNotBlank),
                currency = currency.trim().uppercase(),
                visibility = EventVisibility.fromWireName(visibilityKey.trim())
                    ?: throw IllegalArgumentException("Неизвестная visibility события"),
            )
        }
    }
}
