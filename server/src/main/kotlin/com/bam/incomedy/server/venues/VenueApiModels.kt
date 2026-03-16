package com.bam.incomedy.server.venues

import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallServiceArea
import com.bam.incomedy.domain.venue.HallStage
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.HallZone
import com.bam.incomedy.domain.venue.VenueContact
import com.bam.incomedy.domain.venue.VenueDraft
import com.bam.incomedy.server.db.StoredHallTemplate
import com.bam.incomedy.server.db.StoredVenue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Строгий JSON parser organizer venue payloads и persisted layout blobs. */
internal val venueJson: Json = Json { ignoreUnknownKeys = false }

/** DTO списка площадок. */
@Serializable
data class VenueListResponse(
    val venues: List<VenueResponse>,
)

/** DTO запроса создания площадки. */
@Serializable
data class CreateVenueRequest(
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contacts: List<VenueContactPayload> = emptyList(),
) {
    /** Преобразует create request в domain draft для общей валидации. */
    fun toDomain(): VenueDraft {
        return VenueDraft(
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contacts = contacts.map(VenueContactPayload::toDomain),
        )
    }
}

/** DTO площадки в organizer venue API. */
@Serializable
data class VenueResponse(
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
    companion object {
        /** Маппит stored venue в API response. */
        fun fromStored(storedVenue: StoredVenue): VenueResponse {
            return VenueResponse(
                id = storedVenue.id,
                workspaceId = storedVenue.workspaceId,
                name = storedVenue.name,
                city = storedVenue.city,
                address = storedVenue.address,
                timezone = storedVenue.timezone,
                capacity = storedVenue.capacity,
                description = storedVenue.description,
                contacts = venueJson.decodeFromString<List<VenueContactPayload>>(storedVenue.contactsJson),
                hallTemplates = storedVenue.hallTemplates.map(HallTemplateResponse::fromStored),
            )
        }
    }
}

/** DTO контакта площадки. */
@Serializable
data class VenueContactPayload(
    val label: String,
    val value: String,
) {
    companion object {
        /** Маппит доменный контакт в API DTO. */
        fun fromDomain(contact: VenueContact): VenueContactPayload {
            return VenueContactPayload(
                label = contact.label,
                value = contact.value,
            )
        }
    }

    /** Маппит API DTO контакта в доменную модель. */
    fun toDomain(): VenueContact {
        return VenueContact(
            label = label,
            value = value,
        )
    }
}

/** DTO create/update hall template request. */
@Serializable
data class HallTemplateMutationRequest(
    val name: String,
    val status: String,
    val layout: HallLayoutPayload,
) {
    /** Преобразует request в доменный hall template draft для общей валидации. */
    fun toDomain(): HallTemplateDraft {
        return HallTemplateDraft(
            name = name,
            status = HallTemplateStatus.fromWireName(status) ?: HallTemplateStatus.DRAFT,
            layout = layout.toDomain(),
        )
    }
}

/** DTO hall template response. */
@Serializable
data class HallTemplateResponse(
    val id: String,
    @SerialName("venue_id")
    val venueId: String,
    val name: String,
    val version: Int,
    val status: String,
    val layout: HallLayoutPayload,
) {
    companion object {
        /** Маппит stored hall template в API response. */
        fun fromStored(storedTemplate: StoredHallTemplate): HallTemplateResponse {
            return HallTemplateResponse(
                id = storedTemplate.id,
                venueId = storedTemplate.venueId,
                name = storedTemplate.name,
                version = storedTemplate.version,
                status = storedTemplate.status,
                layout = venueJson.decodeFromString(storedTemplate.layoutJson),
            )
        }
    }
}

/** DTO запроса клонирования hall template. */
@Serializable
data class CloneHallTemplateRequest(
    val name: String? = null,
)

/** DTO канонического hall layout organizer API. */
@Serializable
data class HallLayoutPayload(
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
    /** Маппит API DTO layout в доменную модель. */
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
data class HallStagePayload(
    val label: String,
    val notes: String? = null,
) {
    /** Маппит API DTO сцены в доменную модель. */
    fun toDomain(): HallStage {
        return HallStage(
            label = label,
            notes = notes,
        )
    }
}

/** DTO ценовой зоны. */
@Serializable
data class HallPriceZonePayload(
    val id: String,
    val name: String,
    @SerialName("default_price_minor")
    val defaultPriceMinor: Int? = null,
) {
    /** Маппит API DTO ценовой зоны в доменную модель. */
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
data class HallZonePayload(
    val id: String,
    val name: String,
    val capacity: Int,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
    val kind: String = "standing",
) {
    /** Маппит API DTO зоны в доменную модель. */
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
data class HallRowPayload(
    val id: String,
    val label: String,
    val seats: List<HallSeatPayload>,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
) {
    /** Маппит API DTO ряда в доменную модель. */
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
data class HallSeatPayload(
    val ref: String,
    val label: String,
) {
    /** Маппит API DTO места в доменную модель. */
    fun toDomain(): HallSeat {
        return HallSeat(
            ref = ref,
            label = label,
        )
    }
}

/** DTO стола. */
@Serializable
data class HallTablePayload(
    val id: String,
    val label: String,
    @SerialName("seat_count")
    val seatCount: Int,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
) {
    /** Маппит API DTO стола в доменную модель. */
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
data class HallServiceAreaPayload(
    val id: String,
    val name: String,
    val kind: String,
) {
    /** Маппит API DTO служебной зоны в доменную модель. */
    fun toDomain(): HallServiceArea {
        return HallServiceArea(
            id = id,
            name = name,
            kind = kind,
        )
    }
}
