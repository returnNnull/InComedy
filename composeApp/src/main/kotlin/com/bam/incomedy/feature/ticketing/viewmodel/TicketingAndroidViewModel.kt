package com.bam.incomedy.feature.ticketing.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.feature.ticketing.TicketingState
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер общей модели audience/staff ticketing feature.
 *
 * @property application Android application context.
 */
class TicketingAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая ticketing feature model из KMP-слоя. */
    private val sharedViewModel = InComedyKoin.getTicketingViewModel()

    /** Состояние ticketing feature для Compose UI. */
    val state: StateFlow<TicketingState> = sharedViewModel.state

    init {
        sharedViewModel.loadTickets()
    }

    /** Явно перезагружает список билетов текущего пользователя. */
    fun refreshTickets() {
        sharedViewModel.loadTickets()
    }

    /** Отправляет QR payload на server-side проверку. */
    fun scanTicket(qrPayload: String) {
        sharedViewModel.scanTicket(qrPayload)
    }

    /** Скрывает текущую ticketing-ошибку. */
    fun clearError() {
        sharedViewModel.clearError()
    }

    /** Скрывает последний результат проверки QR. */
    fun clearCheckInResult() {
        sharedViewModel.clearCheckInResult()
    }
}
