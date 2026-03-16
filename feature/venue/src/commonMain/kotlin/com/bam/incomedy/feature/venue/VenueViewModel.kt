package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.VenueDraft
import com.bam.incomedy.domain.venue.VenueDraftValidator
import com.bam.incomedy.domain.venue.VenueManagementService
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
 * Shared MVI-координатор organizer venue management feature.
 *
 * Модель хранит список площадок и выполняет create/update/clone операции, а UI-формы оставляет
 * на стороне платформенных экранов, чтобы не раздувать shared state transient-полями редактора.
 *
 * @property venueManagementService Domain-сервис площадок и шаблонов.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property dispatcher Dispatcher фоновых операций.
 */
class VenueViewModel(
    private val venueManagementService: VenueManagementService,
    private val accessTokenProvider: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Scope общей модели, живущий до завершения процесса приложения. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Mutable backing state venue feature. */
    private val _state = MutableStateFlow(VenueState())

    /** Публичный immutable state venue feature. */
    val state: StateFlow<VenueState> = _state.asStateFlow()

    /** Mutable backing effect stream для toast/snackbar событий. */
    private val _effects = MutableSharedFlow<VenueEffect>(extraBufferCapacity = 1)

    /** Публичный immutable поток одноразовых эффектов. */
    val effects: SharedFlow<VenueEffect> = _effects.asSharedFlow()

    /** Маршрутизирует intents в нужную ветку venue feature. */
    fun onIntent(intent: VenueIntent) {
        when (intent) {
            VenueIntent.LoadVenues -> loadVenues()
            is VenueIntent.CreateVenue -> createVenue(intent.draft)
            is VenueIntent.CreateHallTemplate -> createHallTemplate(intent.venueId, intent.draft)
            is VenueIntent.UpdateHallTemplate -> updateHallTemplate(intent.templateId, intent.draft)
            is VenueIntent.CloneHallTemplate -> cloneHallTemplate(intent.templateId, intent.clonedName)
            VenueIntent.ClearError -> clearError()
        }
    }

    /** Упрощенный вызов загрузки площадок для платформенных wrappers. */
    fun loadVenues() {
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            venueManagementService.listVenues(accessToken).fold(
                onSuccess = { venues ->
                    _state.update { it.copy(venues = venues, isLoading = false, errorMessage = null) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось загрузить площадки",
                        )
                    }
                },
            )
        }
    }

    /** Упрощенный вызов создания площадки для платформенных wrappers. */
    fun createVenue(draft: VenueDraft) {
        val validationError = VenueDraftValidator.validateVenueDraft(draft)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        mutate(
            successMessage = "Площадка сохранена",
            errorMessage = "Не удалось создать площадку",
        ) { accessToken ->
            venueManagementService.createVenue(accessToken = accessToken, draft = draft)
        }
    }

    /** Упрощенный вызов создания hall template для платформенных wrappers. */
    fun createHallTemplate(
        venueId: String,
        draft: HallTemplateDraft,
    ) {
        val validationError = VenueDraftValidator.validateHallTemplateDraft(draft)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        mutate(
            successMessage = "Шаблон зала создан",
            errorMessage = "Не удалось создать шаблон зала",
        ) { accessToken ->
            venueManagementService.createHallTemplate(
                accessToken = accessToken,
                venueId = venueId,
                draft = draft,
            )
        }
    }

    /** Упрощенный вызов обновления hall template для платформенных wrappers. */
    fun updateHallTemplate(
        templateId: String,
        draft: HallTemplateDraft,
    ) {
        val validationError = VenueDraftValidator.validateHallTemplateDraft(draft)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }
        mutate(
            successMessage = "Шаблон зала обновлен",
            errorMessage = "Не удалось обновить шаблон зала",
        ) { accessToken ->
            venueManagementService.updateHallTemplate(
                accessToken = accessToken,
                templateId = templateId,
                draft = draft,
            )
        }
    }

    /** Упрощенный вызов клонирования hall template для платформенных wrappers. */
    fun cloneHallTemplate(
        templateId: String,
        clonedName: String? = null,
    ) {
        mutate(
            successMessage = "Шаблон зала клонирован",
            errorMessage = "Не удалось клонировать шаблон зала",
        ) { accessToken ->
            venueManagementService.cloneHallTemplate(
                accessToken = accessToken,
                templateId = templateId,
                clonedName = clonedName,
            )
        }
    }

    /** Скрывает текущую ошибку venue feature. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Пишет локальную validation/parsing ошибку в state без network roundtrip. */
    fun showLocalError(message: String) {
        _state.update { it.copy(errorMessage = message.take(200)) }
    }

    /**
     * Выполняет venue mutation и затем синхронизирует обновленный список площадок.
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
                    _effects.emit(VenueEffect.MutationCompleted(successMessage))
                    _state.update { current -> current.copy(isSubmitting = false, errorMessage = null) }
                    refreshVenueList(accessToken)
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

    /** Перезагружает список площадок после успешной мутации без повторного loading-indicator режима. */
    private suspend fun refreshVenueList(accessToken: String) {
        venueManagementService.listVenues(accessToken).fold(
            onSuccess = { venues ->
                _state.update { it.copy(venues = venues, errorMessage = null) }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(errorMessage = error.message?.take(200) ?: "Не удалось обновить список площадок")
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
            _state.update { it.copy(errorMessage = "Нет активной organizer-сессии для работы с площадками") }
        }
        return accessToken
    }
}
