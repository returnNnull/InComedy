package com.bam.incomedy.server.ticketing

import com.bam.incomedy.server.db.StoredInventoryUnit
import com.bam.incomedy.server.db.StoredIssuedTicket
import com.bam.incomedy.server.db.StoredSeatHold
import com.bam.incomedy.server.db.StoredTicketCheckInResult
import com.bam.incomedy.server.db.StoredTicketCheckoutSession
import com.bam.incomedy.server.db.StoredTicketOrder
import com.bam.incomedy.server.db.StoredTicketOrderLine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter

/** Строгий JSON parser ticketing foundation и checkout order payload-ов. */
internal val ticketingJson: Json = Json { ignoreUnknownKeys = false }

/** Lenient parser webhook-уведомлений YooKassa, где важен только минимальный envelope. */
internal val yookassaWebhookJson: Json = Json { ignoreUnknownKeys = true }

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

/** DTO запроса создания checkout order-а из активных hold-ов. */
@Serializable
data class CreateTicketOrderRequest(
    @SerialName("hold_ids")
    val holdIds: List<String>,
) {
    /** Возвращает канонический и очищенный список hold id для backend validation. */
    fun toHoldIds(): List<String> {
        return holdIds.map(String::trim).filter(String::isNotBlank)
    }
}

/** DTO запроса check-in по QR payload. */
@Serializable
data class CheckInTicketRequest(
    @SerialName("qr_payload")
    val qrPayload: String,
) {
    /** Возвращает очищенный QR payload для server-side валидации. */
    fun toQrPayload(): String {
        return qrPayload.trim()
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
            currentUserId: String?,
        ): InventoryUnitResponse {
            val activeHold = storedUnit.activeHold
            val heldByCurrentUser = currentUserId != null && activeHold?.userId == currentUserId
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

/** DTO checkout order-а. */
@Serializable
data class TicketOrderResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    val status: String,
    val currency: String,
    @SerialName("total_minor")
    val totalMinor: Int,
    @SerialName("checkout_expires_at")
    val checkoutExpiresAt: String,
    val lines: List<TicketOrderLineResponse>,
) {
    companion object {
        /** Маппит stored checkout order в API response. */
        fun fromStored(storedOrder: StoredTicketOrder): TicketOrderResponse {
            return TicketOrderResponse(
                id = storedOrder.id,
                eventId = storedOrder.eventId,
                status = storedOrder.status,
                currency = storedOrder.currency,
                totalMinor = storedOrder.totalMinor,
                checkoutExpiresAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(storedOrder.checkoutExpiresAt),
                lines = storedOrder.lines.map(TicketOrderLineResponse::fromStored),
            )
        }
    }
}

/** DTO списка выданных билетов. */
@Serializable
data class TicketListResponse(
    val tickets: List<TicketResponse>,
)

/** DTO выданного билета. */
@Serializable
data class TicketResponse(
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
    companion object {
        /** Маппит stored билет в API response и при необходимости скрывает сам QR payload. */
        fun fromStored(
            storedTicket: StoredIssuedTicket,
            includeQrPayload: Boolean = true,
        ): TicketResponse {
            return TicketResponse(
                id = storedTicket.id,
                orderId = storedTicket.orderId,
                eventId = storedTicket.eventId,
                inventoryUnitId = storedTicket.inventoryUnitId,
                inventoryRef = storedTicket.inventoryRef,
                label = storedTicket.label,
                status = storedTicket.status,
                qrPayload = storedTicket.qrPayload.takeIf { includeQrPayload },
                issuedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(storedTicket.issuedAt),
                checkedInAt = storedTicket.checkedInAt?.let(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format),
                checkedInByUserId = storedTicket.checkedInByUserId,
            )
        }
    }
}

/** DTO результата сканирования билета на входе. */
@Serializable
data class TicketCheckInResponse(
    val result: String,
    val ticket: TicketResponse,
) {
    companion object {
        /** Маппит stored результат прохода, не повторяя сам QR payload в ответе checker-у. */
        fun fromStored(
            storedResult: StoredTicketCheckInResult,
        ): TicketCheckInResponse {
            return TicketCheckInResponse(
                result = storedResult.resultCode,
                ticket = TicketResponse.fromStored(
                    storedTicket = storedResult.ticket,
                    includeQrPayload = false,
                ),
            )
        }
    }
}

/** DTO checkout session-а для внешнего PSP handoff. */
@Serializable
data class TicketCheckoutSessionResponse(
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
    companion object {
        /** Маппит stored checkout session в API response. */
        fun fromStored(
            storedSession: StoredTicketCheckoutSession,
            eventId: String,
        ): TicketCheckoutSessionResponse {
            return TicketCheckoutSessionResponse(
                id = storedSession.id,
                orderId = storedSession.orderId,
                eventId = eventId,
                provider = storedSession.provider,
                status = storedSession.status,
                confirmationUrl = storedSession.confirmationUrl,
                checkoutExpiresAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(storedSession.checkoutExpiresAt),
            )
        }
    }
}

/** DTO позиции checkout order-а. */
@Serializable
data class TicketOrderLineResponse(
    @SerialName("inventory_unit_id")
    val inventoryUnitId: String,
    @SerialName("inventory_ref")
    val inventoryRef: String,
    val label: String,
    @SerialName("price_minor")
    val priceMinor: Int,
    val currency: String,
) {
    companion object {
        /** Маппит stored order line в API response. */
        fun fromStored(storedLine: StoredTicketOrderLine): TicketOrderLineResponse {
            return TicketOrderLineResponse(
                inventoryUnitId = storedLine.inventoryUnitId,
                inventoryRef = storedLine.inventoryRef,
                label = storedLine.label,
                priceMinor = storedLine.priceMinor,
                currency = storedLine.currency,
            )
        }
    }
}

/** Минимальный webhook envelope YooKassa, достаточный для безопасной server-side перепроверки. */
@Serializable
data class YooKassaWebhookRequest(
    val type: String,
    val event: String,
    @SerialName("object")
    val payment: YooKassaWebhookPaymentObject,
) {
    /** Возвращает очищенный provider payment id. */
    fun providerPaymentId(): String {
        return payment.id.trim()
    }
}

/** Минимальный объект платежа YooKassa, из которого route берет только идентификатор платежа. */
@Serializable
data class YooKassaWebhookPaymentObject(
    val id: String,
)
