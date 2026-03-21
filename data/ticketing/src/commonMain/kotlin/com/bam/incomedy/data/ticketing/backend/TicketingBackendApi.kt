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
import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.IssuedTicketStatus
import com.bam.incomedy.domain.ticketing.SeatHold
import com.bam.incomedy.domain.ticketing.SeatHoldStatus
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.domain.ticketing.TicketCheckInResultCode
import com.bam.incomedy.domain.ticketing.TicketCheckoutProvider
import com.bam.incomedy.domain.ticketing.TicketCheckoutSession
import com.bam.incomedy.domain.ticketing.TicketCheckoutSessionStatus
import com.bam.incomedy.domain.ticketing.TicketOrder
import com.bam.incomedy.domain.ticketing.TicketOrderLine
import com.bam.incomedy.domain.ticketing.TicketOrderStatus
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
 * Клиент инкапсулирует DTO и backend contract для public/authenticated inventory list и hold
 * create/release, чтобы последующие checkout-итерации могли переиспользовать тот же transport слой.
 */
class TicketingBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Загружает публичный инвентарь события без персонализации hold-ов. */
    suspend fun listPublicInventory(
        eventId: String,
    ): Result<List<InventoryUnit>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/public/events/$eventId/inventory")
            ensureBackendSuccess(response, parser)
            response.body<InventoryListResponse>().inventory.map(InventoryUnitPayload::toDomain)
        }
    }

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

    /** Создает checkout order из активных hold-ов текущего пользователя. */
    suspend fun createTicketOrder(
        accessToken: String,
        eventId: String,
        holdIds: List<String>,
    ): Result<TicketOrder> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/orders") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateTicketOrderRequest(holdIds = holdIds))
            }
            ensureBackendSuccess(response, parser)
            response.body<TicketOrderPayload>().toDomain()
        }
    }

    /** Загружает текущий checkout order текущего пользователя. */
    suspend fun getTicketOrder(
        accessToken: String,
        orderId: String,
    ): Result<TicketOrder> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/orders/$orderId") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<TicketOrderPayload>().toDomain()
        }
    }

    /** Загружает список билетов текущего пользователя. */
    suspend fun listMyTickets(
        accessToken: String,
    ): Result<List<IssuedTicket>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/me/tickets") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<TicketListResponse>().tickets.map(TicketPayload::toDomain)
        }
    }

    /** Стартует внешний checkout для существующего ticket order-а. */
    suspend fun startTicketCheckout(
        accessToken: String,
        eventId: String,
        orderId: String,
    ): Result<TicketCheckoutSession> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/orders/$orderId/checkout") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<TicketCheckoutSessionPayload>().toDomain()
        }
    }

    /** Проверяет билет на входе по QR payload. */
    suspend fun scanTicket(
        accessToken: String,
        qrPayload: String,
    ): Result<TicketCheckInResult> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/checkin/scan") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(CheckInTicketRequest(qrPayload = qrPayload))
            }
            ensureBackendSuccess(response, parser)
            response.body<TicketCheckInResponse>().toDomain()
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

/** DTO списка выданных билетов. */
@Serializable
private data class TicketListResponse(
    val tickets: List<TicketPayload>,
)

/** DTO запроса создания hold-а. */
@Serializable
private data class CreateSeatHoldRequest(
    @SerialName("inventory_ref")
    val inventoryRef: String,
)

/** DTO запроса создания checkout order-а. */
@Serializable
private data class CreateTicketOrderRequest(
    @SerialName("hold_ids")
    val holdIds: List<String>,
)

/** DTO запроса проверки билета по QR payload. */
@Serializable
private data class CheckInTicketRequest(
    @SerialName("qr_payload")
    val qrPayload: String,
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
    /** Маппит transport DTO в доменную inventory model независимо от способа авторизации клиента. */
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

/** DTO checkout order-а. */
@Serializable
private data class TicketOrderPayload(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    val status: String,
    val currency: String,
    @SerialName("total_minor")
    val totalMinor: Int,
    @SerialName("checkout_expires_at")
    val checkoutExpiresAt: String,
    val lines: List<TicketOrderLinePayload>,
) {
    /** Маппит transport DTO заказа в доменную checkout-модель. */
    fun toDomain(): TicketOrder {
        return TicketOrder(
            id = id,
            eventId = eventId,
            status = requireNotNull(TicketOrderStatus.fromWireName(status)),
            currency = currency,
            totalMinor = totalMinor,
            checkoutExpiresAtIso = checkoutExpiresAt,
            lines = lines.map(TicketOrderLinePayload::toDomain),
        )
    }
}

/** DTO checkout session-а для внешнего PSP handoff. */
@Serializable
private data class TicketCheckoutSessionPayload(
    val id: String,
    @SerialName("order_id")
    val orderId: String,
    @SerialName("event_id")
    val eventId: String,
    val provider: String,
    val status: String,
    @SerialName("confirmation_url")
    val confirmationUrl: String,
    @SerialName("checkout_expires_at")
    val checkoutExpiresAt: String,
) {
    /** Маппит transport DTO checkout session в доменную модель. */
    fun toDomain(): TicketCheckoutSession {
        return TicketCheckoutSession(
            id = id,
            orderId = orderId,
            eventId = eventId,
            provider = requireNotNull(TicketCheckoutProvider.fromWireName(provider)),
            status = requireNotNull(TicketCheckoutSessionStatus.fromWireName(status)),
            confirmationUrl = confirmationUrl,
            checkoutExpiresAtIso = checkoutExpiresAt,
        )
    }
}

/** DTO выданного билета. */
@Serializable
private data class TicketPayload(
    val id: String,
    @SerialName("order_id")
    val orderId: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("inventory_unit_id")
    val inventoryUnitId: String,
    @SerialName("inventory_ref")
    val inventoryRef: String,
    val label: String,
    val status: String,
    @SerialName("qr_payload")
    val qrPayload: String? = null,
    @SerialName("issued_at")
    val issuedAt: String,
    @SerialName("checked_in_at")
    val checkedInAt: String? = null,
    @SerialName("checked_in_by_user_id")
    val checkedInByUserId: String? = null,
) {
    /** Маппит transport DTO билета в доменную модель. */
    fun toDomain(): IssuedTicket {
        return IssuedTicket(
            id = id,
            orderId = orderId,
            eventId = eventId,
            inventoryUnitId = inventoryUnitId,
            inventoryRef = inventoryRef,
            label = label,
            status = requireNotNull(IssuedTicketStatus.fromWireName(status)),
            qrPayload = qrPayload,
            issuedAtIso = issuedAt,
            checkedInAtIso = checkedInAt,
            checkedInByUserId = checkedInByUserId,
        )
    }
}

/** DTO ответа сканирования билета на входе. */
@Serializable
private data class TicketCheckInResponse(
    val result: String,
    val ticket: TicketPayload,
) {
    /** Маппит transport DTO результата сканирования в доменную модель. */
    fun toDomain(): TicketCheckInResult {
        return TicketCheckInResult(
            resultCode = requireNotNull(TicketCheckInResultCode.fromWireName(result)),
            ticket = ticket.toDomain(),
        )
    }
}

/** DTO позиции checkout order-а. */
@Serializable
private data class TicketOrderLinePayload(
    @SerialName("inventory_unit_id")
    val inventoryUnitId: String,
    @SerialName("inventory_ref")
    val inventoryRef: String,
    val label: String,
    @SerialName("price_minor")
    val priceMinor: Int,
    val currency: String,
) {
    /** Маппит transport DTO позиции в доменную модель. */
    fun toDomain(): TicketOrderLine {
        return TicketOrderLine(
            inventoryUnitId = inventoryUnitId,
            inventoryRef = inventoryRef,
            label = label,
            priceMinor = priceMinor,
            currency = currency,
        )
    }
}
