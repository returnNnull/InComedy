package com.bam.incomedy.domain.ticketing

/**
 * Checkout session, который переводит пользователя из внутреннего `TicketOrder` во внешний PSP flow.
 *
 * @property id Идентификатор checkout session в backend persistence.
 * @property orderId Идентификатор ticket order-а, для которого стартован checkout.
 * @property eventId Идентификатор события-владельца заказа.
 * @property provider Активный PSP-провайдер.
 * @property status Текущее состояние checkout session.
 * @property confirmationUrl Внешний redirect URL, который должен открыть клиент.
 * @property checkoutExpiresAtIso RFC3339 timestamp истечения исходного order lock-а.
 */
data class TicketCheckoutSession(
    val id: String,
    val orderId: String,
    val eventId: String,
    val provider: TicketCheckoutProvider,
    val status: TicketCheckoutSessionStatus,
    val confirmationUrl: String,
    val checkoutExpiresAtIso: String,
)

/**
 * Активный PSP-провайдер для audience checkout.
 *
 * @property wireName Значение для backend API и transport-слоя.
 */
enum class TicketCheckoutProvider(
    val wireName: String,
) {
    YOOKASSA("yookassa"),
    ;

    companion object {
        /** Восстанавливает провайдера по wire-значению. */
        fun fromWireName(value: String): TicketCheckoutProvider? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Текущее состояние checkout session до финального payment confirmation.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class TicketCheckoutSessionStatus(
    val wireName: String,
) {
    PENDING_REDIRECT("pending_redirect"),
    WAITING_FOR_CAPTURE("waiting_for_capture"),
    EXPIRED("expired"),
    SUCCEEDED("succeeded"),
    CANCELED("canceled"),
    ;

    companion object {
        /** Восстанавливает статус checkout session по wire-значению. */
        fun fromWireName(value: String): TicketCheckoutSessionStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
