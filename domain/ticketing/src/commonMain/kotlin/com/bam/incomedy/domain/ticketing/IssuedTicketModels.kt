package com.bam.incomedy.domain.ticketing

/**
 * Выданный билет поверх уже оплаченного ticket order-а.
 *
 * @property id Идентификатор билета.
 * @property orderId Идентификатор заказа-владельца.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit в persistence.
 * @property inventoryRef Стабильная ссылка на место/слот внутри события.
 * @property label Человекочитаемая подпись билета.
 * @property status Текущее состояние билета.
 * @property qrPayload Непрозрачная строка, которую клиент кодирует в QR; может скрываться из
 *   некоторых backend-ответов, где повторная отдача payload не нужна.
 * @property issuedAtIso RFC3339 timestamp выпуска билета.
 * @property checkedInAtIso RFC3339 timestamp фактического прохода на входе, если он уже был.
 * @property checkedInByUserId Идентификатор сотрудника, отметившего проход.
 */
data class IssuedTicket(
    val id: String,
    val orderId: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val label: String,
    val status: IssuedTicketStatus,
    val qrPayload: String? = null,
    val issuedAtIso: String,
    val checkedInAtIso: String? = null,
    val checkedInByUserId: String? = null,
)

/**
 * Жизненный цикл выданного билета.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class IssuedTicketStatus(
    val wireName: String,
) {
    ISSUED("issued"),
    CHECKED_IN("checked_in"),
    CANCELED("canceled"),
    ;

    companion object {
        /** Восстанавливает статус билета по wire-значению. */
        fun fromWireName(value: String): IssuedTicketStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Результат сканирования QR-кода билета на входе.
 *
 * @property resultCode Низкокардинальный исход проверки.
 * @property ticket Актуальное состояние билета после обработки сканирования.
 */
data class TicketCheckInResult(
    val resultCode: TicketCheckInResultCode,
    val ticket: IssuedTicket,
)

/**
 * Итог проверки билета на входе.
 *
 * @property wireName Значение для backend API.
 */
enum class TicketCheckInResultCode(
    val wireName: String,
) {
    CHECKED_IN("checked_in"),
    DUPLICATE("duplicate"),
    ;

    companion object {
        /** Восстанавливает код результата по wire-значению. */
        fun fromWireName(value: String): TicketCheckInResultCode? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
