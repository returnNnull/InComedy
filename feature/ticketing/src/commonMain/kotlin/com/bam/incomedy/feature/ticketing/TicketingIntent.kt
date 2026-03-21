package com.bam.incomedy.feature.ticketing

/**
 * Пользовательские intents ticketing feature.
 */
sealed interface TicketingIntent {
    /** Загружает список билетов текущего пользователя. */
    data object LoadTickets : TicketingIntent

    /**
     * Отправляет QR payload на серверную проверку для staff flow.
     *
     * @property qrPayload Непрозрачный payload, полученный из QR.
     */
    data class ScanTicket(
        val qrPayload: String,
    ) : TicketingIntent

    /** Очищает текущую верхнеуровневую ошибку. */
    data object ClearError : TicketingIntent

    /** Очищает последний результат проверки QR. */
    data object ClearCheckInResult : TicketingIntent
}
