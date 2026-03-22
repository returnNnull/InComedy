package com.bam.incomedy.feature.lineup

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryOrderUpdate
import com.bam.incomedy.domain.lineup.LineupManagementService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared MVI-координатор comedian applications и organizer lineup management.
 *
 * Модель подготавливает общий state слой для будущих Android/iOS экранов и удерживает в одном
 * месте comedian submit flow, organizer review и lineup reorder поверх уже существующего backend
 * foundation.
 *
 * @property lineupManagementService Domain-сервис applications/lineup bounded context-а.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property dispatcher Dispatcher фоновых операций.
 */
class LineupViewModel(
    private val lineupManagementService: LineupManagementService,
    private val accessTokenProvider: () -> String?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Scope общей модели, живущий до завершения процесса приложения. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Mutable backing state feature-а. */
    private val _state = MutableStateFlow(LineupState())

    /** Публичный immutable state feature-а. */
    val state: StateFlow<LineupState> = _state.asStateFlow()

    /** Маршрутизирует intents в нужную ветку lineup feature. */
    fun onIntent(intent: LineupIntent) {
        when (intent) {
            is LineupIntent.LoadOrganizerContext -> loadOrganizerContext(intent.eventId)
            is LineupIntent.SubmitApplication -> submitApplication(intent.eventId, intent.note)
            is LineupIntent.UpdateApplicationStatus -> updateApplicationStatus(
                eventId = intent.eventId,
                applicationId = intent.applicationId,
                status = intent.status,
            )
            is LineupIntent.ReorderLineup -> reorderLineup(
                eventId = intent.eventId,
                orderedEntryIds = intent.orderedEntryIds,
            )
            LineupIntent.ClearError -> clearError()
        }
    }

    /** Загружает organizer-visible applications и lineup для выбранного события. */
    fun loadOrganizerContext(eventId: String) {
        if (eventId.isBlank()) {
            _state.update { it.copy(errorMessage = "Не выбран event для загрузки lineup context") }
            return
        }
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update {
                it.copy(
                    selectedEventId = eventId,
                    isLoading = true,
                    errorMessage = null,
                )
            }

            val applicationsDeferred = async {
                lineupManagementService.listEventApplications(
                    accessToken = accessToken,
                    eventId = eventId,
                )
            }
            val lineupDeferred = async {
                lineupManagementService.listEventLineup(
                    accessToken = accessToken,
                    eventId = eventId,
                )
            }

            val applicationsResult = applicationsDeferred.await()
            val lineupResult = lineupDeferred.await()
            val error = applicationsResult.exceptionOrNull() ?: lineupResult.exceptionOrNull()
            if (error != null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message?.take(200) ?: "Не удалось загрузить lineup context",
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    selectedEventId = eventId,
                    applications = sortApplications(applicationsResult.getOrDefault(emptyList())),
                    lineup = sortLineup(lineupResult.getOrDefault(emptyList())),
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    /** Отправляет comedian application и локально добавляет ее в state без organizer reload. */
    fun submitApplication(eventId: String, note: String?) {
        if (eventId.isBlank()) {
            _state.update { it.copy(errorMessage = "Не выбран event для отправки заявки") }
            return
        }
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            lineupManagementService.submitApplication(
                accessToken = accessToken,
                eventId = eventId,
                note = note?.trim()?.takeIf(String::isNotBlank),
            ).fold(
                onSuccess = { application ->
                    _state.update {
                        it.copy(
                            selectedEventId = eventId,
                            applications = sortApplications(
                                (it.applications + application)
                                    .distinctBy(ComedianApplication::id),
                            ),
                            isSubmitting = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось отправить заявку",
                        )
                    }
                },
            )
        }
    }

    /** Меняет review-статус и затем перезагружает organizer context, чтобы синхронизировать lineup. */
    fun updateApplicationStatus(
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ) {
        if (eventId.isBlank() || applicationId.isBlank()) {
            _state.update { it.copy(errorMessage = "Не хватает данных для изменения статуса заявки") }
            return
        }
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            lineupManagementService.updateApplicationStatus(
                accessToken = accessToken,
                eventId = eventId,
                applicationId = applicationId,
                status = status,
            ).fold(
                onSuccess = {
                    reloadOrganizerContextAfterMutation(
                        accessToken = accessToken,
                        eventId = eventId,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось изменить статус заявки",
                        )
                    }
                },
            )
        }
    }

    /** Выполняет reorder lineup и обновляет state списком из server response. */
    fun reorderLineup(
        eventId: String,
        orderedEntryIds: List<String>,
    ) {
        if (eventId.isBlank()) {
            _state.update { it.copy(errorMessage = "Не выбран event для перестановки lineup") }
            return
        }
        val normalizedIds = orderedEntryIds.map(String::trim).filter(String::isNotBlank)
        if (normalizedIds.isEmpty()) {
            _state.update { it.copy(errorMessage = "Передайте полный порядок lineup entries") }
            return
        }

        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            lineupManagementService.reorderLineup(
                accessToken = accessToken,
                eventId = eventId,
                entries = normalizedIds.mapIndexed { index, entryId ->
                    LineupEntryOrderUpdate(
                        entryId = entryId,
                        orderIndex = index + 1,
                    )
                },
            ).fold(
                onSuccess = { lineup ->
                    _state.update {
                        it.copy(
                            selectedEventId = eventId,
                            lineup = sortLineup(lineup),
                            isSubmitting = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось переставить lineup",
                        )
                    }
                },
            )
        }
    }

    /** Скрывает текущую ошибку feature-а. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Возвращает текущий access token или пишет понятную ошибку в state. */
    private fun requireAccessToken(): String? {
        val accessToken = accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (accessToken == null) {
            _state.update { it.copy(errorMessage = "Нет активной сессии для работы с lineup feature") }
        }
        return accessToken
    }

    /** Перезагружает organizer context после мутации, чтобы state и lineup оставались консистентными. */
    private suspend fun reloadOrganizerContextAfterMutation(
        accessToken: String,
        eventId: String,
    ) {
        val applicationsResult = lineupManagementService.listEventApplications(
            accessToken = accessToken,
            eventId = eventId,
        )
        val lineupResult = lineupManagementService.listEventLineup(
            accessToken = accessToken,
            eventId = eventId,
        )
        val error = applicationsResult.exceptionOrNull() ?: lineupResult.exceptionOrNull()
        if (error != null) {
            _state.update {
                it.copy(
                    isSubmitting = false,
                    errorMessage = error.message?.take(200) ?: "Не удалось синхронизировать lineup context после мутации",
                )
            }
            return
        }

        _state.update {
            it.copy(
                selectedEventId = eventId,
                applications = sortApplications(applicationsResult.getOrDefault(emptyList())),
                lineup = sortLineup(lineupResult.getOrDefault(emptyList())),
                isSubmitting = false,
                errorMessage = null,
            )
        }
    }

    /** Стабильно сортирует заявки по моменту изменения статуса и созданию. */
    private fun sortApplications(applications: List<ComedianApplication>): List<ComedianApplication> {
        return applications.sortedWith(
            compareByDescending<ComedianApplication> { it.statusUpdatedAtIso }
                .thenByDescending { it.createdAtIso },
        )
    }

    /** Нормализует порядок lineup по explicit `orderIndex`. */
    private fun sortLineup(entries: List<LineupEntry>): List<LineupEntry> {
        return entries.sortedWith(
            compareBy<LineupEntry>({ it.orderIndex }, { it.createdAtIso }),
        )
    }
}
