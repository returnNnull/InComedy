package com.bam.incomedy.data.event.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventHallSnapshot
import com.bam.incomedy.domain.event.EventManagementService
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallServiceArea
import com.bam.incomedy.domain.venue.HallStage
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallZone
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент organizer event management API.
 *
 * Клиент изолирует transport DTO и backend route contract от shared/domain слоев.
 *
 * @property baseUrl Базовый URL backend API.
 * @property parser JSON-парсер DTO и fallback-ошибок.
 * @property httpClient Настроенный Ktor-клиент organizer event surface.
 */
class EventBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Загружает список organizer events, доступных текущей сессии. */
    suspend fun listEvents(accessToken: String): Result<List<OrganizerEvent>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/events") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<EventListResponse>().events.map(EventResponse::toDomain)
        }
    }

    /** Создает organizer event draft с frozen snapshot схемы зала. */
    suspend fun createEvent(
        accessToken: String,
        draft: EventDraft,
    ): Result<OrganizerEvent> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateEventRequest.fromDomain(draft))
            }
            ensureBackendSuccess(response, parser)
            response.body<EventResponse>().toDomain()
        }
    }

    /** Публикует organizer event draft. */
    suspend fun publishEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/publish") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<EventResponse>().toDomain()
        }
    }
}

/** DTO списка событий. */
@Serializable
private data class EventListResponse(
    val events: List<EventResponse>,
)

/** DTO запроса создания события. */
@Serializable
private data class CreateEventRequest(
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
    val currency: String,
    val visibility: String,
) {
    companion object {
        /** Собирает transport request из доменного event draft-а. */
        fun fromDomain(draft: EventDraft): CreateEventRequest {
            return CreateEventRequest(
                workspaceId = draft.workspaceId,
                venueId = draft.venueId,
                hallTemplateId = draft.hallTemplateId,
                title = draft.title,
                description = draft.description,
                startsAt = draft.startsAtIso,
                doorsOpenAt = draft.doorsOpenAtIso,
                endsAt = draft.endsAtIso,
                currency = draft.currency,
                visibility = draft.visibility.wireName,
            )
        }
    }
}

/** DTO organizer event. */
@Serializable
private data class EventResponse(
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
    val hallSnapshot: EventHallSnapshotPayload,
) {
    /** Маппит transport event в доменную модель. */
    fun toDomain(): OrganizerEvent {
        return OrganizerEvent(
            id = id,
            workspaceId = workspaceId,
            venueId = venueId,
            venueName = venueName,
            hallSnapshotId = hallSnapshotId,
            sourceTemplateId = sourceTemplateId,
            sourceTemplateName = sourceTemplateName,
            title = title,
            description = description,
            startsAtIso = startsAt,
            doorsOpenAtIso = doorsOpenAt,
            endsAtIso = endsAt,
            status = EventStatus.fromWireName(status) ?: EventStatus.DRAFT,
            salesStatus = EventSalesStatus.fromWireName(salesStatus) ?: EventSalesStatus.CLOSED,
            currency = currency,
            visibility = EventVisibility.fromWireName(visibility) ?: EventVisibility.PUBLIC,
            hallSnapshot = hallSnapshot.toDomain(),
        )
    }
}

/** DTO frozen snapshot схемы зала. */
@Serializable
private data class EventHallSnapshotPayload(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("source_template_id")
    val sourceTemplateId: String,
    val layout: HallLayoutPayload,
) {
    /** Маппит transport snapshot в доменную модель. */
    fun toDomain(): EventHallSnapshot {
        return EventHallSnapshot(
            id = id,
            eventId = eventId,
            sourceTemplateId = sourceTemplateId,
            layout = layout.toDomain(),
        )
    }
}

/** DTO typed hall layout для event snapshot contract-а. */
@Serializable
private data class HallLayoutPayload(
    val stage: HallStagePayload? = null,
    @SerialName("price_zones")
    val priceZones: List<HallPriceZonePayload> = emptyList(),
    val zones: List<HallZonePayload> = emptyList(),
    val rows: List<HallRowPayload> = emptyList(),
    val tables: List<HallTablePayload> = emptyList(),
    @SerialName("service_areas")
    val serviceAreas: List<HallServiceAreaPayload> = emptyList(),
    @SerialName("blocked_seat_refs")
    val blockedSeatRefs: List<String> = emptyList(),
) {
    /** Маппит transport layout в доменную модель. */
    fun toDomain(): HallLayout {
        return HallLayout(
            stage = stage?.toDomain(),
            priceZones = priceZones.map(HallPriceZonePayload::toDomain),
            zones = zones.map(HallZonePayload::toDomain),
            rows = rows.map(HallRowPayload::toDomain),
            tables = tables.map(HallTablePayload::toDomain),
            serviceAreas = serviceAreas.map(HallServiceAreaPayload::toDomain),
            blockedSeatRefs = blockedSeatRefs,
        )
    }
}

/** DTO сцены. */
@Serializable
private data class HallStagePayload(
    val label: String,
    val notes: String? = null,
) {
    /** Маппит transport DTO сцены в доменную модель. */
    fun toDomain(): HallStage {
        return HallStage(
            label = label,
            notes = notes,
        )
    }
}

/** DTO ценовой зоны. */
@Serializable
private data class HallPriceZonePayload(
    val id: String,
    val name: String,
    @SerialName("default_price_minor")
    val defaultPriceMinor: Int? = null,
) {
    /** Маппит transport DTO ценовой зоны в доменную модель. */
    fun toDomain(): HallPriceZone {
        return HallPriceZone(
            id = id,
            name = name,
            defaultPriceMinor = defaultPriceMinor,
        )
    }
}

/** DTO standing/sector зоны. */
@Serializable
private data class HallZonePayload(
    val id: String,
    val name: String,
    val capacity: Int,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
    val kind: String = "standing",
) {
    /** Маппит transport DTO зоны в доменную модель. */
    fun toDomain(): HallZone {
        return HallZone(
            id = id,
            name = name,
            capacity = capacity,
            priceZoneId = priceZoneId,
            kind = kind,
        )
    }
}

/** DTO ряда. */
@Serializable
private data class HallRowPayload(
    val id: String,
    val label: String,
    val seats: List<HallSeatPayload>,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
) {
    /** Маппит transport DTO ряда в доменную модель. */
    fun toDomain(): HallRow {
        return HallRow(
            id = id,
            label = label,
            seats = seats.map(HallSeatPayload::toDomain),
            priceZoneId = priceZoneId,
        )
    }
}

/** DTO места. */
@Serializable
private data class HallSeatPayload(
    val ref: String,
    val label: String,
) {
    /** Маппит transport DTO места в доменную модель. */
    fun toDomain(): HallSeat {
        return HallSeat(
            ref = ref,
            label = label,
        )
    }
}

/** DTO стола. */
@Serializable
private data class HallTablePayload(
    val id: String,
    val label: String,
    @SerialName("seat_count")
    val seatCount: Int,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
) {
    /** Маппит transport DTO стола в доменную модель. */
    fun toDomain(): HallTable {
        return HallTable(
            id = id,
            label = label,
            seatCount = seatCount,
            priceZoneId = priceZoneId,
        )
    }
}

/** DTO служебной зоны. */
@Serializable
private data class HallServiceAreaPayload(
    val id: String,
    val name: String,
    val kind: String,
) {
    /** Маппит transport DTO служебной зоны в доменную модель. */
    fun toDomain(): HallServiceArea {
        return HallServiceArea(
            id = id,
            name = name,
            kind = kind,
        )
    }
}
