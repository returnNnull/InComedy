package com.bam.incomedy.data.ticketing.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.ticketing.InventorySnapshotTargetType
import com.bam.incomedy.domain.ticketing.InventoryStatus
import com.bam.incomedy.domain.ticketing.InventoryType
import com.bam.incomedy.domain.ticketing.InventoryUnit
import com.bam.incomedy.domain.ticketing.SeatHold
import com.bam.incomedy.domain.ticketing.SeatHoldStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент ticketing foundation API.
 *
 * Клиент инкапсулирует DTO и backend contract для inventory list и hold create/release, чтобы
 * последующие checkout-итерации могли переиспользовать тот же transport слой.
 */
class TicketingBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Загружает текущий инвентарь события. */
    suspend fun listInventory(
        accessToken: String,
        eventId: String,
    ): Result<List<InventoryUnit>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/events/$eventId/inventory") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<InventoryListResponse>().inventory.map(InventoryUnitPayload::toDomain)
        }
    }

    /** Создает hold на одну inventory unit. */
    suspend fun createSeatHold(
        accessToken: String,
        eventId: String,
        inventoryRef: String,
    ): Result<SeatHold> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/holds") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateSeatHoldRequest(inventoryRef = inventoryRef))
            }
            ensureBackendSuccess(response, parser)
            response.body<SeatHoldPayload>().toDomain()
        }
    }

    /** Освобождает hold по его идентификатору. */
    suspend fun releaseSeatHold(
        accessToken: String,
        holdId: String,
    ): Result<SeatHold> {
        return runCatching {
            val response = httpClient.delete("$baseUrl/api/v1/holds/$holdId") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<SeatHoldPayload>().toDomain()
        }
    }
}

/** DTO списка inventory units. */
@Serializable
private data class InventoryListResponse(
    val inventory: List<InventoryUnitPayload>,
)

/** DTO запроса создания hold-а. */
@Serializable
private data class CreateSeatHoldRequest(
    @SerialName("inventory_ref")
    val inventoryRef: String,
)

/** DTO inventory unit. */
@Serializable
private data class InventoryUnitPayload(
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
    /** Маппит transport DTO в доменную inventory model. */
    fun toDomain(): InventoryUnit {
        return InventoryUnit(
            id = id,
            eventId = eventId,
            inventoryRef = inventoryRef,
            inventoryType = requireNotNull(InventoryType.fromWireName(inventoryType)),
            snapshotTargetType = requireNotNull(InventorySnapshotTargetType.fromWireName(snapshotTargetType)),
            snapshotTargetRef = snapshotTargetRef,
            label = label,
            priceZoneId = priceZoneId,
            priceZoneName = priceZoneName,
            priceMinor = priceMinor,
            currency = currency,
            status = requireNotNull(InventoryStatus.fromWireName(status)),
            activeHoldId = activeHoldId,
            holdExpiresAtIso = holdExpiresAt,
            heldByCurrentUser = heldByCurrentUser,
        )
    }
}

/** DTO hold-а. */
@Serializable
private data class SeatHoldPayload(
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
    /** Маппит transport DTO hold-а в доменную модель. */
    fun toDomain(): SeatHold {
        return SeatHold(
            id = id,
            eventId = eventId,
            inventoryUnitId = inventoryUnitId,
            inventoryRef = inventoryRef,
            expiresAtIso = expiresAt,
            status = requireNotNull(SeatHoldStatus.fromWireName(status)),
        )
    }
}
