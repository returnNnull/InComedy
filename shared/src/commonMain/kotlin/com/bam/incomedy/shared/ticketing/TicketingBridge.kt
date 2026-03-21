package com.bam.incomedy.shared.ticketing

import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.feature.ticketing.TicketingState
import com.bam.incomedy.feature.ticketing.TicketingViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей ticketing feature model для Swift-слоя.
 *
 * Bridge скрывает typed Kotlin state/effect потоки и дает SwiftUI стабильные snapshot-модели и
 * высокоуровневые команды загрузки билетов и staff check-in.
 *
 * @property viewModel Общая ticketing feature model.
 */
class TicketingBridge(
    private val viewModel: TicketingViewModel = InComedyKoin.getTicketingViewModel(),
) : BaseFeatureBridge() {
    /** Возвращает текущее состояние ticketing feature единым снимком. */
    fun currentState(): TicketingStateSnapshot = viewModel.state.value.toSnapshot()

    /** Подписывает Swift-слой на обновления ticketing state. */
    fun observeState(onState: (TicketingStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    /** Загружает список билетов текущего пользователя. */
    fun loadTickets() {
        viewModel.loadTickets()
    }

    /** Проверяет QR payload через общий ticketing feature. */
    fun scanTicket(qrPayload: String) {
        viewModel.scanTicket(qrPayload)
    }

    /** Очищает текущую ticketing-ошибку. */
    fun clearError() {
        viewModel.clearError()
    }

    /** Очищает последний результат проверки QR. */
    fun clearCheckInResult() {
        viewModel.clearCheckInResult()
    }
}

/** Преобразует внутреннее состояние ticketing feature в export-friendly snapshot. */
private fun TicketingState.toSnapshot(): TicketingStateSnapshot {
    return TicketingStateSnapshot(
        tickets = tickets.map(IssuedTicket::toSnapshot),
        isLoading = isLoading,
        isScanning = isScanning,
        lastCheckInResult = lastCheckInResult?.toSnapshot(),
        errorMessage = errorMessage,
    )
}

/** Преобразует доменный билет в iOS-friendly snapshot. */
private fun IssuedTicket.toSnapshot(): TicketSnapshot {
    return TicketSnapshot(
        id = id,
        orderId = orderId,
        eventId = eventId,
        inventoryUnitId = inventoryUnitId,
        inventoryRef = inventoryRef,
        label = label,
        statusKey = status.wireName,
        qrPayload = qrPayload,
        issuedAtIso = issuedAtIso,
        checkedInAtIso = checkedInAtIso,
        checkedInByUserId = checkedInByUserId,
    )
}

/** Преобразует доменный результат check-in в iOS-friendly snapshot. */
private fun TicketCheckInResult.toSnapshot(): TicketCheckInResultSnapshot {
    return TicketCheckInResultSnapshot(
        resultCodeKey = resultCode.wireName,
        ticket = ticket.toSnapshot(),
    )
}
