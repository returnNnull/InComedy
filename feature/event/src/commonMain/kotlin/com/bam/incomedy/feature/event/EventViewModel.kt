package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventDraftValidator
import com.bam.incomedy.domain.event.EventManagementService
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.domain.venue.VenueManagementService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared MVI-координатор organizer event management feature.
 *
 * Модель хранит список событий и reference venues/templates, а transient form state оставляет на
 * стороне платформенных экранов, чтобы shared слой не раздувался UI-деталями.
 *
 * @property eventManagementService Domain-сервис organizer events.
 * @property venueManagementService Domain-сервис upstream venues/templates reference data.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property dispatcher Dispatcher фоновых операций.
 */
class EventViewModel(
    private val eventManagementService: EventManagementService,
    private val venueManagementService: VenueManagementService,
    private val accessTokenProvider: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Scope общей модели, живущий до завершения процесса приложения. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Mutable backing state event feature. */
    private val _state = MutableStateFlow(EventState())

    /** Публичный immutable state event feature. */
    val state: StateFlow<EventState> = _state.asStateFlow()

    /** Mutable backing effect stream для toast/snackbar событий. */
    private val _effects = MutableSharedFlow<EventEffect>(extraBufferCapacity = 1)

    /** Публичный immutable поток одноразовых эффектов. */
    val effects: SharedFlow<EventEffect> = _effects.asSharedFlow()

    /** Маршрутизирует intents в нужную ветку event feature. */
    fun onIntent(intent: EventIntent) {
        when (intent) {
            EventIntent.LoadContext -> loadContext()
            is EventIntent.CreateEvent -> createEvent(intent.draft)
            is EventIntent.UpdateEvent -> updateEvent(intent.eventId, intent.draft)
            is EventIntent.PublishEvent -> publishEvent(intent.eventId)
            EventIntent.ClearError -> clearError()
        }
    }

    /** Упрощенный вызов полной загрузки event context для платформенных wrappers. */
    fun loadContext() {
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val venuesDeferred = async { venueManagementService.listVenues(accessToken) }
            val eventsDeferred = async { eventManagementService.listEvents(accessToken) }

            val venuesResult = venuesDeferred.await()
            val eventsResult = eventsDeferred.await()
            val error = venuesResult.exceptionOrNull() ?: eventsResult.exceptionOrNull()
            if (error != null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message?.take(200) ?: "Не удалось загрузить события",
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    venues = venuesResult.getOrDefault(emptyList()),
                    events = eventsResult.getOrDefault(emptyList()),
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    /** Упрощенный вызов создания organizer event draft. */
    fun createEvent(draft: EventDraft) {
        val validationError = EventDraftValidator.validateEventDraft(draft)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        mutate(
            successMessage = "Событие сохранено в черновик",
            errorMessage = "Не удалось создать событие",
        ) { accessToken ->
            eventManagementService.createEvent(accessToken = accessToken, draft = draft)
        }
    }

    /** Упрощенный вызов обновления organizer event details и event-local overrides. */
    fun updateEvent(
        eventId: String,
        draft: EventUpdateDraft,
    ) {
        mutate(
            successMessage = "Событие обновлено",
            errorMessage = "Не удалось обновить событие",
        ) { accessToken ->
            eventManagementService.updateEvent(
                accessToken = accessToken,
                eventId = eventId,
                draft = draft,
            )
        }
    }

    /** Упрощенный вызов публикации organizer event. */
    fun publishEvent(eventId: String) {
        mutate(
            successMessage = "Событие опубликовано",
            errorMessage = "Не удалось опубликовать событие",
        ) { accessToken ->
            eventManagementService.publishEvent(accessToken = accessToken, eventId = eventId)
        }
    }

    /** Скрывает текущую ошибку event feature. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Пишет локальную validation/parsing ошибку в state без network roundtrip. */
    fun showLocalError(message: String) {
        _state.update { it.copy(errorMessage = message.take(200)) }
    }

    /**
     * Выполняет event mutation и затем синхронизирует обновленный список событий.
     *
     * @param successMessage Текст успешного эффекта.
     * @param errorMessage Fallback-ошибка для UI.
     * @param action Suspended action с access token текущей сессии.
     */
    private fun mutate(
        successMessage: String,
        errorMessage: String,
        action: suspend (String) -> Result<*>,
    ) {
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            action(accessToken).fold(
                onSuccess = {
                    _effects.emit(EventEffect.MutationCompleted(successMessage))
                    _state.update { current -> current.copy(isSubmitting = false, errorMessage = null) }
                    refreshEvents(accessToken)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message?.take(200) ?: errorMessage,
                        )
                    }
                },
            )
        }
    }

    /** Перезагружает только список событий после успешной мутации. */
    private suspend fun refreshEvents(accessToken: String) {
        eventManagementService.listEvents(accessToken).fold(
            onSuccess = { events ->
                _state.update { it.copy(events = events, errorMessage = null) }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(errorMessage = error.message?.take(200) ?: "Не удалось обновить список событий")
                }
            },
        )
    }

    /** Возвращает текущий access token или пишет понятную ошибку в state. */
    private fun requireAccessToken(): String? {
        val accessToken = accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (accessToken == null) {
            _state.update { it.copy(errorMessage = "Нет активной organizer-сессии для работы с событиями") }
        }
        return accessToken
    }
}
