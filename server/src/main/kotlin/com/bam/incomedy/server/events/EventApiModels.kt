package com.bam.incomedy.server.events

import com.bam.incomedy.domain.event.EventAvailabilityOverride
import com.bam.incomedy.domain.event.EventAvailabilityStatus
import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventHallSnapshot
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredEventPricingAssignment
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.venues.HallLayoutPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/** Строгий JSON parser organizer event payloads и persisted snapshot blobs. */
internal val eventJson: Json = Json { ignoreUnknownKeys = false }

/** Декодирует сохраненный snapshot JSON в typed layout payload. */
internal fun decodeStoredSnapshotLayout(snapshotJson: String): HallLayoutPayload {
    return eventJson.decodeFromString(snapshotJson)
}

/** DTO списка organizer events. */
@Serializable
data class EventListResponse(
    val events: List<OrganizerEventResponse>,
)

/** DTO списка публичных audience-safe мероприятий. */
@Serializable
data class PublicEventListResponse(
    val events: List<PublicEventSummaryResponse>,
)

/** DTO запроса создания organizer event. */
@Serializable
data class CreateEventRequest(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("venue_id")
    val venueId: String,
    @SerialName("hall_template_id")
    val hallTemplateId: String,
    val title: String,
    val description: String? = null,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("doors_open_at")
    val doorsOpenAt: String? = null,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val currency: String = "RUB",
    val visibility: String = EventVisibility.PUBLIC.wireName,
) {
    /** Преобразует transport request в доменный draft для общей валидации. */
    fun toDomain(): EventDraft {
        return EventDraft(
            workspaceId = workspaceId.trim(),
            venueId = venueId.trim(),
            hallTemplateId = hallTemplateId.trim(),
            title = title.trim(),
            description = description?.trim()?.takeIf(String::isNotBlank),
            startsAtIso = startsAt.trim(),
            doorsOpenAtIso = doorsOpenAt?.trim()?.takeIf(String::isNotBlank),
            endsAtIso = endsAt?.trim()?.takeIf(String::isNotBlank),
            currency = currency.trim().uppercase(),
            visibility = EventVisibility.fromWireName(visibility.trim()) ?: EventVisibility.PUBLIC,
        )
    }
}

/** DTO полного обновления organizer event поверх frozen snapshot. */
@Serializable
data class UpdateEventRequest(
    val title: String,
    val description: String? = null,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("doors_open_at")
    val doorsOpenAt: String? = null,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val currency: String,
    val visibility: String = EventVisibility.PUBLIC.wireName,
    @SerialName("price_zones")
    val priceZones: List<EventPriceZoneRequest> = emptyList(),
    @SerialName("pricing_assignments")
    val pricingAssignments: List<EventPricingAssignmentRequest> = emptyList(),
    @SerialName("availability_overrides")
    val availabilityOverrides: List<EventAvailabilityOverrideRequest> = emptyList(),
) {
    /** Преобразует transport request в доменный update draft. */
    fun toDomain(): EventUpdateDraft {
        return EventUpdateDraft(
            title = title.trim(),
            description = description?.trim()?.takeIf(String::isNotBlank),
            startsAtIso = startsAt.trim(),
            doorsOpenAtIso = doorsOpenAt?.trim()?.takeIf(String::isNotBlank),
            endsAtIso = endsAt?.trim()?.takeIf(String::isNotBlank),
            currency = currency.trim().uppercase(),
            visibility = EventVisibility.fromWireName(visibility.trim()) ?: EventVisibility.PUBLIC,
            priceZones = priceZones.map(EventPriceZoneRequest::toDomain),
            pricingAssignments = pricingAssignments.map(EventPricingAssignmentRequest::toDomain),
            availabilityOverrides = availabilityOverrides.map(EventAvailabilityOverrideRequest::toDomain),
        )
    }
}

/** DTO event-local price zone mutation. */
@Serializable
data class EventPriceZoneRequest(
    val id: String,
    val name: String,
    @SerialName("price_minor")
    val priceMinor: Int,
    val currency: String,
    @SerialName("sales_start_at")
    val salesStartAt: String? = null,
    @SerialName("sales_end_at")
    val salesEndAt: String? = null,
    @SerialName("source_template_price_zone_id")
    val sourceTemplatePriceZoneId: String? = null,
) {
    /** Преобразует transport DTO зоны в доменную модель. */
    fun toDomain(): EventPriceZone {
        return EventPriceZone(
            id = id.trim(),
            name = name.trim(),
            priceMinor = priceMinor,
            currency = currency.trim().uppercase(),
            salesStartAtIso = salesStartAt?.trim()?.takeIf(String::isNotBlank),
            salesEndAtIso = salesEndAt?.trim()?.takeIf(String::isNotBlank),
            sourceTemplatePriceZoneId = sourceTemplatePriceZoneId?.trim()?.takeIf(String::isNotBlank),
        )
    }
}

/** DTO pricing assignment mutation. */
@Serializable
data class EventPricingAssignmentRequest(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_ref")
    val targetRef: String,
    @SerialName("event_price_zone_id")
    val eventPriceZoneId: String,
) {
    /** Преобразует transport DTO assignment-а в доменную модель. */
    fun toDomain(): EventPricingAssignment {
        return EventPricingAssignment(
            targetType = EventOverrideTargetType.fromWireName(targetType.trim())
                ?: throw IllegalArgumentException("Неизвестный target type"),
            targetRef = targetRef.trim(),
            eventPriceZoneId = eventPriceZoneId.trim(),
        )
    }
}

/** DTO availability override mutation. */
@Serializable
data class EventAvailabilityOverrideRequest(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_ref")
    val targetRef: String,
    @SerialName("availability_status")
    val availabilityStatus: String,
) {
    /** Преобразует transport DTO availability override-а в доменную модель. */
    fun toDomain(): EventAvailabilityOverride {
        return EventAvailabilityOverride(
            targetType = EventOverrideTargetType.fromWireName(targetType.trim())
                ?: throw IllegalArgumentException("Неизвестный target type"),
            targetRef = targetRef.trim(),
            availabilityStatus = EventAvailabilityStatus.fromWireName(availabilityStatus.trim())
                ?: throw IllegalArgumentException("Неизвестный availability status"),
        )
    }
}

/** DTO organizer event response. */
@Serializable
data class OrganizerEventResponse(
    val id: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("venue_id")
    val venueId: String,
    @SerialName("venue_name")
    val venueName: String,
    @SerialName("hall_snapshot_id")
    val hallSnapshotId: String,
    @SerialName("source_template_id")
    val sourceTemplateId: String,
    @SerialName("source_template_name")
    val sourceTemplateName: String,
    val title: String,
    val description: String? = null,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("doors_open_at")
    val doorsOpenAt: String? = null,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val status: String,
    @SerialName("sales_status")
    val salesStatus: String,
    val currency: String,
    val visibility: String,
    @SerialName("hall_snapshot")
    val hallSnapshot: EventHallSnapshotResponse,
    @SerialName("price_zones")
    val priceZones: List<EventPriceZoneResponse> = emptyList(),
    @SerialName("pricing_assignments")
    val pricingAssignments: List<EventPricingAssignmentResponse> = emptyList(),
    @SerialName("availability_overrides")
    val availabilityOverrides: List<EventAvailabilityOverrideResponse> = emptyList(),
) {
    companion object {
        /** Маппит stored organizer event в API response. */
        fun fromStored(storedEvent: StoredOrganizerEvent): OrganizerEventResponse {
            return OrganizerEventResponse(
                id = storedEvent.id,
                workspaceId = storedEvent.workspaceId,
                venueId = storedEvent.venueId,
                venueName = storedEvent.venueName,
                hallSnapshotId = storedEvent.hallSnapshot.id,
                sourceTemplateId = storedEvent.hallSnapshot.sourceTemplateId,
                sourceTemplateName = storedEvent.hallSnapshot.sourceTemplateName,
                title = storedEvent.title,
                description = storedEvent.description,
                startsAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(storedEvent.startsAt),
                doorsOpenAt = storedEvent.doorsOpenAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                endsAt = storedEvent.endsAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                status = storedEvent.status,
                salesStatus = storedEvent.salesStatus,
                currency = storedEvent.currency,
                visibility = storedEvent.visibility,
                hallSnapshot = EventHallSnapshotResponse.fromStored(storedEvent.hallSnapshot),
                priceZones = storedEvent.priceZones.map(EventPriceZoneResponse::fromStored),
                pricingAssignments = storedEvent.pricingAssignments.map(EventPricingAssignmentResponse::fromStored),
                availabilityOverrides = storedEvent.availabilityOverrides.map(EventAvailabilityOverrideResponse::fromStored),
            )
        }
    }
}

/** DTO audience-safe карточки публичного discovery-каталога. */
@Serializable
data class PublicEventSummaryResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("venue_name")
    val venueName: String,
    val city: String,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("doors_open_at")
    val doorsOpenAt: String? = null,
    @SerialName("ends_at")
    val endsAt: String? = null,
    @SerialName("sales_status")
    val salesStatus: String,
    val currency: String,
    @SerialName("price_min_minor")
    val priceMinMinor: Int? = null,
    @SerialName("price_max_minor")
    val priceMaxMinor: Int? = null,
) {
    companion object {
        /** Маппит internal discovery view в публичный response DTO. */
        fun fromView(view: PublicEventSummaryView): PublicEventSummaryResponse {
            return PublicEventSummaryResponse(
                id = view.id,
                title = view.title,
                description = view.description,
                venueName = view.venueName,
                city = view.city,
                startsAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(view.startsAt),
                doorsOpenAt = view.doorsOpenAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                endsAt = view.endsAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                salesStatus = view.salesStatus,
                currency = view.currency,
                priceMinMinor = view.priceMinMinor,
                priceMaxMinor = view.priceMaxMinor,
            )
        }
    }
}

/** DTO frozen hall snapshot внутри organizer event response. */
@Serializable
data class EventHallSnapshotResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("source_template_id")
    val sourceTemplateId: String,
    @SerialName("source_template_name")
    val sourceTemplateName: String,
    val layout: HallLayoutPayload,
) {
    companion object {
        /** Маппит stored snapshot в API response. */
        fun fromStored(storedSnapshot: StoredEventHallSnapshot): EventHallSnapshotResponse {
            return EventHallSnapshotResponse(
                id = storedSnapshot.id,
                eventId = storedSnapshot.eventId,
                sourceTemplateId = storedSnapshot.sourceTemplateId,
                sourceTemplateName = storedSnapshot.sourceTemplateName,
                layout = decodeStoredSnapshotLayout(storedSnapshot.snapshotJson),
            )
        }
    }
}

/** DTO event-local price zone в organizer event response. */
@Serializable
data class EventPriceZoneResponse(
    val id: String,
    val name: String,
    @SerialName("price_minor")
    val priceMinor: Int,
    val currency: String,
    @SerialName("sales_start_at")
    val salesStartAt: String? = null,
    @SerialName("sales_end_at")
    val salesEndAt: String? = null,
    @SerialName("source_template_price_zone_id")
    val sourceTemplatePriceZoneId: String? = null,
) {
    companion object {
        /** Маппит stored зону в response DTO. */
        fun fromStored(storedZone: StoredEventPriceZone): EventPriceZoneResponse {
            return EventPriceZoneResponse(
                id = storedZone.id,
                name = storedZone.name,
                priceMinor = storedZone.priceMinor,
                currency = storedZone.currency,
                salesStartAt = storedZone.salesStartAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                salesEndAt = storedZone.salesEndAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                sourceTemplatePriceZoneId = storedZone.sourceTemplatePriceZoneId,
            )
        }
    }
}

/** DTO event-local pricing assignment в organizer event response. */
@Serializable
data class EventPricingAssignmentResponse(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_ref")
    val targetRef: String,
    @SerialName("event_price_zone_id")
    val eventPriceZoneId: String,
) {
    companion object {
        /** Маппит stored assignment в response DTO. */
        fun fromStored(storedAssignment: StoredEventPricingAssignment): EventPricingAssignmentResponse {
            return EventPricingAssignmentResponse(
                targetType = storedAssignment.targetType,
                targetRef = storedAssignment.targetRef,
                eventPriceZoneId = storedAssignment.eventPriceZoneId,
            )
        }
    }
}

/** DTO event-local availability override в organizer event response. */
@Serializable
data class EventAvailabilityOverrideResponse(
    @SerialName("target_type")
    val targetType: String,
    @SerialName("target_ref")
    val targetRef: String,
    @SerialName("availability_status")
    val availabilityStatus: String,
) {
    companion object {
        /** Маппит stored availability override в response DTO. */
        fun fromStored(storedOverride: StoredEventAvailabilityOverride): EventAvailabilityOverrideResponse {
            return EventAvailabilityOverrideResponse(
                targetType = storedOverride.targetType,
                targetRef = storedOverride.targetRef,
                availabilityStatus = storedOverride.availabilityStatus,
            )
        }
    }
}
