package com.bam.incomedy.domain.ticketing

/**
 * Provider-agnostic checkout order, собранный из уже удерживаемых inventory unit-ов.
 *
 * @property id Идентификатор заказа.
 * @property eventId Идентификатор события-владельца.
 * @property status Текущее состояние checkout order.
 * @property currency Валюта заказа.
 * @property totalMinor Итоговая сумма заказа в minor units.
 * @property checkoutExpiresAtIso RFC3339 timestamp автоматического истечения checkout lock-а.
 * @property lines Зафиксированные позиции заказа.
 */
data class TicketOrder(
    val id: String,
    val eventId: String,
    val status: TicketOrderStatus,
    val currency: String,
    val totalMinor: Int,
    val checkoutExpiresAtIso: String,
    val lines: List<TicketOrderLine>,
)

/**
 * Зафиксированная позиция checkout order-а.
 *
 * @property inventoryUnitId Идентификатор inventory unit в persistence.
 * @property inventoryRef Стабильная ссылка на ticketing unit.
 * @property label Человекочитаемая подпись позиции.
 * @property priceMinor Цена позиции в minor units.
 * @property currency Валюта позиции.
 */
data class TicketOrderLine(
    val inventoryUnitId: String,
    val inventoryRef: String,
    val label: String,
    val priceMinor: Int,
    val currency: String,
)

/**
 * Жизненный цикл checkout order foundation.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class TicketOrderStatus(
    val wireName: String,
) {
    AWAITING_PAYMENT("awaiting_payment"),
    EXPIRED("expired"),
    PAID("paid"),
    CANCELED("canceled"),
    ;

    companion object {
        /** Восстанавливает статус заказа по wire-значению. */
        fun fromWireName(value: String): TicketOrderStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
