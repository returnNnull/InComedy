package com.bam.incomedy.server.ticketing

import com.bam.incomedy.server.db.StoredInventoryUnit
import com.bam.incomedy.server.db.StoredSeatHold
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/** Строгий JSON parser ticketing foundation payload-ов. */
internal val ticketingJson: Json = Json { ignoreUnknownKeys = false }

/** DTO списка inventory units. */
@Serializable
data class InventoryListResponse(
    val inventory: List<InventoryUnitResponse>,
)

/** DTO запроса создания hold-а. */
@Serializable
data class CreateSeatHoldRequest(
    @SerialName("inventory_ref")
    val inventoryRef: String,
) {
    /** Возвращает каноническую inventory ref для backend validation. */
    fun toInventoryRef(): String {
        return inventoryRef.trim()
    }
}

/** DTO inventory unit в ticketing API. */
@Serializable
data class InventoryUnitResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("inventory_ref")
    val inventoryRef: String,
    @SerialName("inventory_type")
    val inventoryType: String,
    @SerialName("snapshot_target_type")
    val snapshotTargetType: String,
    @SerialName("snapshot_target_ref")
    val snapshotTargetRef: String,
    val label: String,
    @SerialName("price_zone_id")
    val priceZoneId: String? = null,
    @SerialName("price_zone_name")
    val priceZoneName: String? = null,
    @SerialName("price_minor")
    val priceMinor: Int? = null,
    val currency: String,
    val status: String,
    @SerialName("active_hold_id")
    val activeHoldId: String? = null,
    @SerialName("hold_expires_at")
    val holdExpiresAt: String? = null,
    @SerialName("held_by_current_user")
    val heldByCurrentUser: Boolean = false,
) {
    companion object {
        /** Маппит stored inventory unit в response, скрывая чужие hold identifiers. */
        fun fromStored(
            storedUnit: StoredInventoryUnit,
            currentUserId: String,
        ): InventoryUnitResponse {
            val activeHold = storedUnit.activeHold
            val heldByCurrentUser = activeHold?.userId == currentUserId
            return InventoryUnitResponse(
                id = storedUnit.id,
                eventId = storedUnit.eventId,
                inventoryRef = storedUnit.inventoryRef,
                inventoryType = storedUnit.inventoryType,
                snapshotTargetType = storedUnit.snapshotTargetType,
                snapshotTargetRef = storedUnit.snapshotTargetRef,
                label = storedUnit.label,
                priceZoneId = storedUnit.priceZoneId,
                priceZoneName = storedUnit.priceZoneName,
                priceMinor = storedUnit.priceMinor,
                currency = storedUnit.currency,
                status = storedUnit.status,
                activeHoldId = activeHold?.id?.takeIf { heldByCurrentUser },
                holdExpiresAt = activeHold
                    ?.expiresAt
                    ?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format)
                    ?.takeIf { heldByCurrentUser },
                heldByCurrentUser = heldByCurrentUser,
            )
        }
    }
}

/** DTO hold-а. */
@Serializable
data class SeatHoldResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("inventory_unit_id")
    val inventoryUnitId: String,
    @SerialName("inventory_ref")
    val inventoryRef: String,
    @SerialName("expires_at")
    val expiresAt: String,
    val status: String,
) {
    companion object {
        /** Маппит stored hold в API response. */
        fun fromStored(storedHold: StoredSeatHold): SeatHoldResponse {
            return SeatHoldResponse(
                id = storedHold.id,
                eventId = storedHold.eventId,
                inventoryUnitId = storedHold.inventoryUnitId,
                inventoryRef = storedHold.inventoryRef,
                expiresAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(storedHold.expiresAt),
                status = storedHold.status,
            )
        }
    }
}
