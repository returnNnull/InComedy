package com.bam.incomedy.feature.ticketing

import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.TicketCheckInResult

/**
 * Single source of truth клиентского ticketing feature.
 *
 * @property tickets Список билетов текущего пользователя для экрана `Мои билеты`.
 * @property isLoading Показывает, что выполняется загрузка списка билетов.
 * @property isScanning Показывает, что сейчас идет server-side проверка QR payload.
 * @property lastCheckInResult Последний результат staff check-in, если он уже был получен.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class TicketingState(
    val tickets: List<IssuedTicket> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val lastCheckInResult: TicketCheckInResult? = null,
    val errorMessage: String? = null,
)
