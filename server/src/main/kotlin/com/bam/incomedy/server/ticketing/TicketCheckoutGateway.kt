package com.bam.incomedy.server.ticketing

/**
 * Абстракция внешнего PSP checkout handoff поверх внутреннего `TicketOrder`.
 *
 * Реализации отвечают только за создание внешнего checkout session и не должны зашивать
 * provider-специфику в `TicketOrder` или inventory semantics.
 */
interface TicketCheckoutGateway {
    /** Активный provider wire-name для diagnostics, persistence и API responses. */
    val provider: String

    /** Создает внешний checkout session для уже зафиксированного pending order-а. */
    fun createCheckoutSession(request: TicketCheckoutGatewayRequest): TicketCheckoutGatewayResponse

    /** Возвращает актуальное состояние платежа во внешнем PSP для webhook/status verification. */
    fun getPayment(providerPaymentId: String): TicketCheckoutGatewayPaymentSnapshot
}

/**
 * Входная модель для старта внешнего checkout-а.
 *
 * @property orderId Идентификатор ticket order-а.
 * @property eventId Идентификатор события-владельца.
 * @property currency Валюта заказа.
 * @property totalMinor Итоговая сумма заказа в minor units.
 * @property description Безопасное описание платежа для PSP.
 * @property requestId Request correlation identifier для server diagnostics.
 */
data class TicketCheckoutGatewayRequest(
    val orderId: String,
    val eventId: String,
    val currency: String,
    val totalMinor: Int,
    val description: String,
    val requestId: String,
)

/**
 * Результат успешного старта внешнего checkout-а.
 *
 * @property provider Wire-name активного PSP.
 * @property providerPaymentId Идентификатор платежа во внешнем провайдере.
 * @property providerStatus Статус, который вернул провайдер при создании платежа.
 * @property confirmationUrl Redirect URL, который должен открыть клиент.
 * @property returnUrl Merchant-controlled URL возврата после PSP handoff.
 */
data class TicketCheckoutGatewayResponse(
    val provider: String,
    val providerPaymentId: String,
    val providerStatus: String,
    val confirmationUrl: String,
    val returnUrl: String,
)

/**
 * Актуальный snapshot внешнего платежа, полученный непосредственно из PSP.
 *
 * @property provider Активный PSP provider wire-name.
 * @property providerPaymentId Идентификатор платежа в PSP.
 * @property status Актуальный статус внешнего платежа.
 * @property orderId Значение `metadata.order_id`, возвращенное провайдером.
 * @property eventId Значение `metadata.event_id`, возвращенное провайдером.
 * @property totalMinor Итоговая сумма платежа в minor units.
 * @property currency Валюта платежа.
 */
data class TicketCheckoutGatewayPaymentSnapshot(
    val provider: String,
    val providerPaymentId: String,
    val status: String,
    val orderId: String,
    val eventId: String,
    val totalMinor: Int,
    val currency: String,
)

/**
 * Сигнализирует о безопасно классифицированной ошибке внешнего checkout provider-а.
 *
 * @property safeCode Низкокардинальный код для route mapping и diagnostics.
 */
class TicketCheckoutGatewayException(
    val safeCode: String,
    safeMessage: String,
) : IllegalStateException(safeMessage)
