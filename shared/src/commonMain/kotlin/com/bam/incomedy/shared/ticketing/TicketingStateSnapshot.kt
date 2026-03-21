package com.bam.incomedy.shared.ticketing

/**
 * Export-friendly состояние audience/staff ticketing feature для Swift-слоя.
 *
 * @property tickets Список билетов текущего пользователя.
 * @property isLoading Показывает загрузку `Мои билеты`.
 * @property isScanning Показывает активную server-side проверку QR.
 * @property lastCheckInResult Последний результат проверки билета, если он есть.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class TicketingStateSnapshot(
    val tickets: List<TicketSnapshot>,
    val isLoading: Boolean,
    val isScanning: Boolean,
    val lastCheckInResult: TicketCheckInResultSnapshot?,
    val errorMessage: String?,
)

/**
 * Export-friendly билет для Swift-слоя.
 */
data class TicketSnapshot(
    val id: String,
    val orderId: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val label: String,
    val statusKey: String,
    val qrPayload: String?,
    val issuedAtIso: String,
    val checkedInAtIso: String?,
    val checkedInByUserId: String?,
)

/**
 * Export-friendly результат staff check-in.
 */
data class TicketCheckInResultSnapshot(
    val resultCodeKey: String,
    val ticket: TicketSnapshot,
)
