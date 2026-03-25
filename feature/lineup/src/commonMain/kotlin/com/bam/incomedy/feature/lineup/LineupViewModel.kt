package com.bam.incomedy.feature.lineup

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryOrderUpdate
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.domain.lineup.LineupLiveEntry
import com.bam.incomedy.domain.lineup.LineupLiveUpdate
import com.bam.incomedy.domain.lineup.LineupManagementService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * месте comedian submit flow, organizer review, lineup reorder и live-stage mutation поверх уже
 * существующего backend foundation.
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

    /** Хранит флаг platform lifecycle, который разрешает держать realtime feed активным. */
    private var liveUpdatesActive: Boolean = false

    /** Текущий event id для активной realtime-подписки. */
    private var liveUpdatesEventId: String? = null

    /** Активная realtime job для public live-event feed-а. */
    private var liveUpdatesJob: Job? = null

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
            is LineupIntent.UpdateLineupEntryStatus -> updateLineupEntryStatus(
                eventId = intent.eventId,
                entryId = intent.entryId,
                status = intent.status,
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
        if (liveUpdatesEventId != null && liveUpdatesEventId != eventId) {
            stopLiveUpdatesCollection()
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
            ensureLiveUpdatesSubscription(eventId)
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

    /** Меняет live-stage статус записи lineup и сохраняет актуальный organizer lineup в state. */
    fun updateLineupEntryStatus(
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ) {
        if (eventId.isBlank() || entryId.isBlank()) {
            _state.update { it.copy(errorMessage = "Не хватает данных для изменения live-stage статуса") }
            return
        }

        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            lineupManagementService.updateLineupEntryStatus(
                accessToken = accessToken,
                eventId = eventId,
                entryId = entryId,
                status = status,
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
                            errorMessage = error.message?.take(200) ?: "Не удалось изменить live-stage статус",
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

    /** Включает или выключает runtime-подписку на public live updates для активного экрана. */
    fun setLiveUpdatesActive(isActive: Boolean) {
        liveUpdatesActive = isActive
        if (!isActive) {
            stopLiveUpdatesCollection()
            return
        }
        val eventId = state.value.selectedEventId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return
        ensureLiveUpdatesSubscription(eventId)
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

    /** Запускает realtime feed только для активного platform lifecycle и выбранного события. */
    private fun ensureLiveUpdatesSubscription(eventId: String) {
        if (!liveUpdatesActive) return
        if (liveUpdatesEventId == eventId && liveUpdatesJob?.isActive == true) return

        stopLiveUpdatesCollection()
        liveUpdatesEventId = eventId
        liveUpdatesJob = scope.launch {
            lineupManagementService.observeEventLiveUpdates(eventId).collect { update ->
                if (update.eventId != eventId) return@collect
                if (state.value.selectedEventId != eventId) return@collect

                applyLiveUpdate(update)
                if (update.reason == "application_approved") {
                    refreshApplicationsAfterLiveApproval(eventId)
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (liveUpdatesJob === job) {
                    liveUpdatesJob = null
                }
                if (liveUpdatesEventId == eventId) {
                    liveUpdatesEventId = null
                }
            }
        }
    }

    /** Останавливает realtime feed при уходе экрана со сцены или смене event context-а. */
    private fun stopLiveUpdatesCollection() {
        liveUpdatesJob?.cancel()
        liveUpdatesJob = null
        liveUpdatesEventId = null
    }

    /** Применяет audience-safe live payload к текущему organizer lineup state. */
    private fun applyLiveUpdate(update: LineupLiveUpdate) {
        _state.update { currentState ->
            if (currentState.selectedEventId != update.eventId) {
                return@update currentState
            }

            currentState.copy(
                lineup = mergeLiveSummaryIntoLineup(
                    eventId = update.eventId,
                    occurredAtIso = update.occurredAtIso,
                    existingEntries = currentState.lineup,
                    liveEntries = update.summary.lineup,
                ),
                errorMessage = null,
            )
        }
    }

    /**
     * При approval live channel знает про новый lineup entry, но organizer applications требуют
     * обычный authenticated reload.
     */
    private suspend fun refreshApplicationsAfterLiveApproval(eventId: String) {
        val accessToken = currentAccessToken() ?: return
        lineupManagementService.listEventApplications(
            accessToken = accessToken,
            eventId = eventId,
        ).fold(
            onSuccess = { applications ->
                _state.update { currentState ->
                    if (currentState.selectedEventId != eventId) {
                        return@update currentState
                    }
                    currentState.copy(
                        applications = sortApplications(applications),
                        errorMessage = null,
                    )
                }
            },
            onFailure = { error ->
                _state.update { currentState ->
                    if (currentState.selectedEventId != eventId) {
                        return@update currentState
                    }
                    currentState.copy(
                        errorMessage = error.message?.take(200)
                            ?: "Не удалось синхронизировать заявки после live update",
                    )
                }
            },
        )
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

    /** Достает токен без записи ошибки в UI, когда это всего лишь best-effort синхронизация. */
    private fun currentAccessToken(): String? {
        return accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    /** Накладывает public live summary на organizer lineup, сохраняя уже известные поля записи. */
    private fun mergeLiveSummaryIntoLineup(
        eventId: String,
        occurredAtIso: String,
        existingEntries: List<LineupEntry>,
        liveEntries: List<LineupLiveEntry>,
    ): List<LineupEntry> {
        val existingById = existingEntries.associateBy(LineupEntry::id)
        val merged = liveEntries.map { liveEntry ->
            val existing = existingById[liveEntry.id]
            if (existing != null) {
                existing.copy(
                    comedianDisplayName = liveEntry.comedianDisplayName,
                    orderIndex = liveEntry.orderIndex,
                    status = liveEntry.status,
                    updatedAtIso = occurredAtIso,
                )
            } else {
                LineupEntry(
                    id = liveEntry.id,
                    eventId = eventId,
                    comedianUserId = "",
                    comedianDisplayName = liveEntry.comedianDisplayName,
                    comedianUsername = null,
                    applicationId = null,
                    orderIndex = liveEntry.orderIndex,
                    status = liveEntry.status,
                    notes = null,
                    createdAtIso = occurredAtIso,
                    updatedAtIso = occurredAtIso,
                )
            }
        }
        return sortLineup(merged)
    }
}
