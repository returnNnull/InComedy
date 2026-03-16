package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventOverrideValidator
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent

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

    /**
     * Собирает доменный draft обновления события из platform-friendly editor values.
     */
    fun toEventUpdateDraft(
        event: OrganizerEvent,
        title: String,
        description: String?,
        startsAtIso: String,
        doorsOpenAtIso: String?,
        endsAtIso: String?,
        currency: String,
        visibilityKey: String,
        priceZonesText: String,
        pricingAssignmentsText: String,
        availabilityOverridesText: String,
    ): Result<EventUpdateDraft> {
        return runCatching {
            val draft = EventUpdateDraft(
                title = title.trim(),
                description = description?.trim()?.takeIf(String::isNotBlank),
                startsAtIso = startsAtIso.trim(),
                doorsOpenAtIso = doorsOpenAtIso?.trim()?.takeIf(String::isNotBlank),
                endsAtIso = endsAtIso?.trim()?.takeIf(String::isNotBlank),
                currency = currency.trim().uppercase(),
                visibility = EventVisibility.fromWireName(visibilityKey.trim())
                    ?: throw IllegalArgumentException("Неизвестная visibility события"),
                priceZones = EventOverrideEditorCodec.parsePriceZones(priceZonesText),
                pricingAssignments = EventOverrideEditorCodec.parsePricingAssignments(pricingAssignmentsText),
                availabilityOverrides = EventOverrideEditorCodec.parseAvailabilityOverrides(availabilityOverridesText),
            )
            val validationError = EventOverrideValidator.validateEventUpdateDraft(
                draft = draft,
                snapshotLayout = event.hallSnapshot.layout,
            )
            if (validationError != null) {
                error(validationError)
            }
            draft
        }
    }
}
