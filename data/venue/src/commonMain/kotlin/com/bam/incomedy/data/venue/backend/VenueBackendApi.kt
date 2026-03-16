package com.bam.incomedy.data.venue.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallServiceArea
import com.bam.incomedy.domain.venue.HallStage
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.HallZone
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.domain.venue.VenueContact
import com.bam.incomedy.domain.venue.VenueDraft
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент organizer venue management API.
 *
 * Клиент изолирует transport DTO и backend route contract от shared/domain слоев.
 *
 * @property baseUrl Базовый URL backend API.
 * @property parser JSON-парсер DTO и fallback-ошибок.
 * @property httpClient Настроенный Ktor-клиент organizer venue surface.
 */
class VenueBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Загружает список площадок, доступных текущей сессии. */
    suspend fun listVenues(accessToken: String): Result<List<OrganizerVenue>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/venues") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<VenueListResponse>().venues.map(VenueResponse::toDomain)
        }
    }

    /** Создает площадку внутри organizer workspace. */
    suspend fun createVenue(
        accessToken: String,
        draft: VenueDraft,
    ): Result<OrganizerVenue> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/venues") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateVenueRequest.fromDomain(draft))
            }
            ensureBackendSuccess(response, parser)
            response.body<VenueResponse>().toDomain()
        }
    }

    /** Создает hall template внутри выбранной площадки. */
    suspend fun createHallTemplate(
        accessToken: String,
        venueId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/venues/$venueId/hall-templates") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(HallTemplateMutationRequest.fromDomain(draft))
            }
            ensureBackendSuccess(response, parser)
            response.body<HallTemplateResponse>().toDomain()
        }
    }

    /** Обновляет существующий hall template. */
    suspend fun updateHallTemplate(
        accessToken: String,
        templateId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate> {
        return runCatching {
            val response = httpClient.patch("$baseUrl/api/v1/hall-templates/$templateId") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(HallTemplateMutationRequest.fromDomain(draft))
            }
            ensureBackendSuccess(response, parser)
            response.body<HallTemplateResponse>().toDomain()
        }
    }

    /** Клонирует hall template внутри той же площадки. */
    suspend fun cloneHallTemplate(
        accessToken: String,
        templateId: String,
        clonedName: String?,
    ): Result<HallTemplate> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/hall-templates/$templateId/clone") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CloneHallTemplateRequest(name = clonedName))
            }
            ensureBackendSuccess(response, parser)
            response.body<HallTemplateResponse>().toDomain()
        }
    }
}

/** DTO списка площадок. */
@Serializable
private data class VenueListResponse(
    val venues: List<VenueResponse>,
)

/** DTO запроса создания площадки. */
@Serializable
private data class CreateVenueRequest(
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contacts: List<VenueContactRequest> = emptyList(),
) {
    companion object {
        /** Собирает transport request из доменного draft-а. */
        fun fromDomain(draft: VenueDraft): CreateVenueRequest {
            return CreateVenueRequest(
                workspaceId = draft.workspaceId,
                name = draft.name,
                city = draft.city,
                address = draft.address,
                timezone = draft.timezone,
                capacity = draft.capacity,
                description = draft.description,
                contacts = draft.contacts.map(VenueContactRequest::fromDomain),
            )
        }
    }
}

/** DTO контакта площадки. */
@Serializable
private data class VenueContactRequest(
    val label: String,
    val value: String,
) {
    companion object {
        /** Маппит доменный контакт площадки в transport request. */
        fun fromDomain(contact: VenueContact): VenueContactRequest {
            return VenueContactRequest(
                label = contact.label,
                value = contact.value,
            )
        }
    }
}

/** DTO запроса create/update hall template. */
@Serializable
private data class HallTemplateMutationRequest(
    val name: String,
    val status: String,
    val layout: HallLayoutPayload,
) {
    companion object {
        /** Собирает mutation request из доменного hall template draft-а. */
        fun fromDomain(draft: HallTemplateDraft): HallTemplateMutationRequest {
            return HallTemplateMutationRequest(
                name = draft.name,
                status = draft.status.wireName,
                layout = HallLayoutPayload.fromDomain(draft.layout),
            )
        }
    }
}

/** DTO запроса клонирования hall template. */
@Serializable
private data class CloneHallTemplateRequest(
    val name: String? = null,
)

/** DTO площадки. */
@Serializable
private data class VenueResponse(
    val id: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contacts: List<VenueContactPayload> = emptyList(),
    @SerialName("hall_templates")
    val hallTemplates: List<HallTemplateResponse> = emptyList(),
) {
    /** Маппит transport-площадку в доменную модель. */
    fun toDomain(): OrganizerVenue {
        return OrganizerVenue(
            id = id,
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contacts = contacts.map(VenueContactPayload::toDomain),
            hallTemplates = hallTemplates.map(HallTemplateResponse::toDomain),
        )
    }
}

/** DTO hall template. */
@Serializable
private data class HallTemplateResponse(
    val id: String,
    @SerialName("venue_id")
    val venueId: String,
    val name: String,
    val version: Int,
    val status: String,
    val layout: HallLayoutPayload,
) {
    /** Маппит transport template в доменную модель. */
    fun toDomain(): HallTemplate {
        return HallTemplate(
            id = id,
            venueId = venueId,
            name = name,
            version = version,
            status = HallTemplateStatus.fromWireName(status) ?: HallTemplateStatus.DRAFT,
            layout = layout.toDomain(),
        )
    }
}

/** DTO контакта площадки в ответе. */
@Serializable
private data class VenueContactPayload(
    val label: String,
    val value: String,
) {
    /** Маппит transport contact в доменную модель. */
    fun toDomain(): VenueContact {
        return VenueContact(
            label = label,
            value = value,
        )
    }
}

/** DTO typed hall layout для backend contract-а. */
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
    companion object {
        /** Маппит доменный layout в transport DTO. */
        fun fromDomain(layout: HallLayout): HallLayoutPayload {
            return HallLayoutPayload(
                stage = layout.stage?.let(HallStagePayload::fromDomain),
                priceZones = layout.priceZones.map(HallPriceZonePayload::fromDomain),
                zones = layout.zones.map(HallZonePayload::fromDomain),
                rows = layout.rows.map(HallRowPayload::fromDomain),
                tables = layout.tables.map(HallTablePayload::fromDomain),
                serviceAreas = layout.serviceAreas.map(HallServiceAreaPayload::fromDomain),
                blockedSeatRefs = layout.blockedSeatRefs,
            )
        }
    }

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
    companion object {
        /** Маппит доменную сцену в transport DTO. */
        fun fromDomain(stage: HallStage): HallStagePayload {
            return HallStagePayload(
                label = stage.label,
                notes = stage.notes,
            )
        }
    }

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
    companion object {
        /** Маппит доменную ценовую зону в transport DTO. */
        fun fromDomain(zone: HallPriceZone): HallPriceZonePayload {
            return HallPriceZonePayload(
                id = zone.id,
                name = zone.name,
                defaultPriceMinor = zone.defaultPriceMinor,
            )
        }
    }

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
    companion object {
        /** Маппит доменную standing/sector зону в transport DTO. */
        fun fromDomain(zone: HallZone): HallZonePayload {
            return HallZonePayload(
                id = zone.id,
                name = zone.name,
                capacity = zone.capacity,
                priceZoneId = zone.priceZoneId,
                kind = zone.kind,
            )
        }
    }

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
    companion object {
        /** Маппит доменный ряд в transport DTO. */
        fun fromDomain(row: HallRow): HallRowPayload {
            return HallRowPayload(
                id = row.id,
                label = row.label,
                seats = row.seats.map(HallSeatPayload::fromDomain),
                priceZoneId = row.priceZoneId,
            )
        }
    }

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
    companion object {
        /** Маппит доменное место в transport DTO. */
        fun fromDomain(seat: HallSeat): HallSeatPayload {
            return HallSeatPayload(
                ref = seat.ref,
                label = seat.label,
            )
        }
    }

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
    companion object {
        /** Маппит доменный стол в transport DTO. */
        fun fromDomain(table: HallTable): HallTablePayload {
            return HallTablePayload(
                id = table.id,
                label = table.label,
                seatCount = table.seatCount,
                priceZoneId = table.priceZoneId,
            )
        }
    }

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

/** DTO служебной области. */
@Serializable
private data class HallServiceAreaPayload(
    val id: String,
    val name: String,
    val kind: String,
) {
    companion object {
        /** Маппит доменную служебную область в transport DTO. */
        fun fromDomain(area: HallServiceArea): HallServiceAreaPayload {
            return HallServiceAreaPayload(
                id = area.id,
                name = area.name,
                kind = area.kind,
            )
        }
    }

    /** Маппит transport DTO служебной области в доменную модель. */
    fun toDomain(): HallServiceArea {
        return HallServiceArea(
            id = id,
            name = name,
            kind = kind,
        )
    }
}
