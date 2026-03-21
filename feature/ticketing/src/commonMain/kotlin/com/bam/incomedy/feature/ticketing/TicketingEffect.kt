package com.bam.incomedy.feature.ticketing

/**
 * Одноразовые эффекты ticketing feature.
 */
sealed interface TicketingEffect {
    /**
     * Сообщает UI, что очередная проверка QR завершилась и можно показать transient feedback.
     *
     * @property message Краткий текст результата проверки.
     */
    data class ScanCompleted(
        val message: String,
    ) : TicketingEffect
}
