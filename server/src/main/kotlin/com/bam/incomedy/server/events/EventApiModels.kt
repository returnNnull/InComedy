package com.bam.incomedy.server.events

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.server.db.StoredEventHallSnapshot
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.venues.HallLayoutPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/** Строгий JSON parser organizer event payloads и persisted snapshot blobs. */
internal val eventJson: Json = Json { ignoreUnknownKeys = false }

/** DTO списка organizer events. */
@Serializable
data class EventListResponse(
    val events: List<OrganizerEventResponse>,
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
                layout = eventJson.decodeFromString(storedSnapshot.snapshotJson),
            )
        }
    }
}
