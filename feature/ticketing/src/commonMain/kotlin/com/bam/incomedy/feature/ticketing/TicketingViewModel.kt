package com.bam.incomedy.feature.ticketing

import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.IssuedTicketStatus
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.domain.ticketing.TicketCheckInResultCode
import com.bam.incomedy.domain.ticketing.TicketingService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared MVI-координатор audience/staff ticketing surface.
 *
 * Модель отвечает за загрузку `Мои билеты` и staff-friendly QR check-in поверх уже готовых
 * backend-контрактов, не смешивая этот поток с organizer event/venue управлением.
 *
 * @property ticketingService Domain-сервис ticketing API.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property dispatcher Dispatcher фоновых операций.
 */
class TicketingViewModel(
    private val ticketingService: TicketingService,
    private val accessTokenProvider: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Scope общей модели, живущий до завершения процесса приложения. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Mutable backing state ticketing feature. */
    private val _state = MutableStateFlow(TicketingState())

    /** Публичный immutable state ticketing feature. */
    val state: StateFlow<TicketingState> = _state.asStateFlow()

    /** Mutable backing effect stream для transient scan feedback. */
    private val _effects = MutableSharedFlow<TicketingEffect>(extraBufferCapacity = 1)

    /** Публичный immutable поток одноразовых эффектов. */
    val effects: SharedFlow<TicketingEffect> = _effects.asSharedFlow()

    /** Маршрутизирует intents в нужную ветку ticketing feature. */
    fun onIntent(intent: TicketingIntent) {
        when (intent) {
            TicketingIntent.LoadTickets -> loadTickets()
            is TicketingIntent.ScanTicket -> scanTicket(intent.qrPayload)
            TicketingIntent.ClearError -> clearError()
            TicketingIntent.ClearCheckInResult -> clearCheckInResult()
        }
    }

    /** Загружает список билетов текущего пользователя. */
    fun loadTickets() {
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            ticketingService.listMyTickets(accessToken).fold(
                onSuccess = { tickets ->
                    _state.update {
                        it.copy(
                            tickets = sortTickets(tickets),
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось загрузить билеты",
                        )
                    }
                },
            )
        }
    }

    /** Отправляет QR payload на server-side проверку для check-in flow. */
    fun scanTicket(qrPayload: String) {
        val normalizedPayload = qrPayload.trim()
        if (normalizedPayload.isBlank()) {
            _state.update { it.copy(errorMessage = "Введите QR payload для проверки билета") }
            return
        }

        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update {
                it.copy(
                    isScanning = true,
                    lastCheckInResult = null,
                    errorMessage = null,
                )
            }
            ticketingService.scanTicket(
                accessToken = accessToken,
                qrPayload = normalizedPayload,
            ).fold(
                onSuccess = { result ->
                    _effects.emit(
                        TicketingEffect.ScanCompleted(
                            message = scanOutcomeMessage(result),
                        ),
                    )
                    _state.update {
                        it.copy(
                            isScanning = false,
                            lastCheckInResult = result,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось проверить билет",
                        )
                    }
                },
            )
        }
    }

    /** Скрывает текущую ticketing-ошибку. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Скрывает последний результат проверки QR. */
    fun clearCheckInResult() {
        _state.update { it.copy(lastCheckInResult = null) }
    }

    /** Возвращает текущий access token или пишет понятную ошибку в state. */
    private fun requireAccessToken(): String? {
        val accessToken = accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (accessToken == null) {
            _state.update { it.copy(errorMessage = "Нет активной сессии для работы с билетами") }
        }
        return accessToken
    }

    /** Сортирует билеты так, чтобы непройденные билеты оставались наверху списка. */
    private fun sortTickets(tickets: List<IssuedTicket>): List<IssuedTicket> {
        return tickets.sortedWith(
            compareBy<IssuedTicket>(
                { ticketStatusSortOrder(it.status) },
                { it.label.lowercase() },
            ).thenByDescending { it.issuedAtIso },
        )
    }

    /** Возвращает стабильный порядок статусов для списка билетов. */
    private fun ticketStatusSortOrder(status: IssuedTicketStatus): Int {
        return when (status) {
            IssuedTicketStatus.ISSUED -> 0
            IssuedTicketStatus.CHECKED_IN -> 1
            IssuedTicketStatus.CANCELED -> 2
        }
    }

    /** Строит короткое сообщение для transient feedback после сканирования. */
    private fun scanOutcomeMessage(result: TicketCheckInResult): String {
        return when (result.resultCode) {
            TicketCheckInResultCode.CHECKED_IN -> "Гость успешно отмечен на входе"
            TicketCheckInResultCode.DUPLICATE -> "Билет уже был отмечен ранее"
        }
    }
}
