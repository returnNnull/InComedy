package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SessionContextService
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Общая модель сессии, которая синхронизирует auth-состояние с ролями и рабочими пространствами.
 *
 * @property authViewModel Общая модель авторизации, выступающая источником токенов и базовой сессии.
 * @property sessionContextService Сервис ролей и рабочих пространств.
 * @property dispatcher Dispatcher для фоновых операций модели.
 */
class SessionViewModel(
    private val authViewModel: AuthViewModel,
    private val sessionContextService: SessionContextService,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Scope модели, живущий до ручной очистки. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Внутреннее mutable-состояние расширенного контекста сессии. */
    private val _state = MutableStateFlow(SessionState())

    /** Публичное состояние авторизованной части приложения. */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Последний access token, для которого уже был инициирован контекстный запрос. */
    private var observedAccessToken: String? = null

    init {
        scope.launch {
            authViewModel.state.collect { authState ->
                val session = authState.session
                if (session == null) {
                    observedAccessToken = null
                    _state.value = SessionState()
                    return@collect
                }
                val tokenChanged = observedAccessToken != session.accessToken
                observedAccessToken = session.accessToken
                _state.update { current ->
                    current.copy(
                        isAuthorized = true,
                        provider = session.provider,
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        userId = session.userId,
                        displayName = session.user.displayName,
                        username = session.user.username,
                        photoUrl = session.user.photoUrl,
                        roles = session.user.roles,
                        activeRole = session.user.activeRole,
                        linkedProviders = session.user.linkedProviders,
                        workspaces = if (tokenChanged) emptyList() else current.workspaces,
                        isLoadingContext = if (tokenChanged) true else current.isLoadingContext,
                        isUpdatingRole = false,
                        isCreatingWorkspace = false,
                    )
                }
                if (tokenChanged) {
                    loadWorkspaces(
                        accessToken = session.accessToken,
                        showLoadingState = true,
                    )
                }
            }
        }
    }

    /** Восстанавливает сессию по сохраненному access token. */
    fun restoreSessionToken(accessToken: String) {
        authViewModel.onIntent(AuthIntent.OnRestoreSessionToken(accessToken))
    }

    /** Запускает выход пользователя через общий auth-слой. */
    fun signOut() {
        authViewModel.onIntent(AuthIntent.OnSignOut)
    }

    /** Переключает активную роль и синхронизирует результат с общей auth-сессией. */
    fun setActiveRole(role: String) {
        val normalizedRole = role.trim().lowercase()
        val currentSession = authViewModel.state.value.session ?: return
        if (normalizedRole.isBlank()) return
        if (currentSession.user.activeRole == normalizedRole) return

        scope.launch {
            _state.update { it.copy(isUpdatingRole = true, errorMessage = null) }
            sessionContextService.setActiveRole(
                accessToken = currentSession.accessToken,
                role = normalizedRole,
            ).fold(
                onSuccess = { roleContext ->
                    applyRoleContext(
                        session = currentSession,
                        roleContext = roleContext,
                    )
                    _state.update { it.copy(isUpdatingRole = false, errorMessage = null) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isUpdatingRole = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось сменить роль",
                        )
                    }
                },
            )
        }
    }

    /** Создает новое рабочее пространство и перезагружает зависимый контекст сессии. */
    fun createWorkspace(name: String, slug: String? = null) {
        val currentSession = authViewModel.state.value.session ?: return
        val normalizedName = name.trim()
        val normalizedSlug = slug?.trim()?.takeIf(String::isNotBlank)
        if (normalizedName.length !in 3..80) {
            _state.update { it.copy(errorMessage = "Название рабочего пространства должно быть от 3 до 80 символов") }
            return
        }

        scope.launch {
            _state.update { it.copy(isCreatingWorkspace = true, errorMessage = null) }
            sessionContextService.createWorkspace(
                accessToken = currentSession.accessToken,
                name = normalizedName,
                slug = normalizedSlug,
            ).fold(
                onSuccess = { workspace ->
                    _state.update { current ->
                        current.copy(
                            workspaces = current.workspaces
                                .filterNot { it.id == workspace.id } + workspace,
                            isCreatingWorkspace = false,
                            errorMessage = null,
                        )
                    }
                    refreshRoleContext(currentSession)
                    loadWorkspaces(
                        accessToken = currentSession.accessToken,
                        showLoadingState = false,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCreatingWorkspace = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось создать рабочее пространство",
                        )
                    }
                },
            )
        }
    }

    /** Очищает текущую UI-ошибку контекста. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Освобождает ресурсы модели. */
    fun clear() {
        scope.cancel()
    }

    /** Загружает список рабочих пространств и при необходимости показывает состояние загрузки. */
    private fun loadWorkspaces(
        accessToken: String,
        showLoadingState: Boolean,
    ) {
        scope.launch {
            if (showLoadingState) {
                _state.update { it.copy(isLoadingContext = true, errorMessage = null) }
            }
            sessionContextService.listWorkspaces(accessToken).fold(
                onSuccess = { workspaces ->
                    _state.update {
                        it.copy(
                            workspaces = workspaces,
                            isLoadingContext = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isLoadingContext = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось загрузить рабочие пространства",
                        )
                    }
                },
            )
        }
    }

    /** Перезагружает роли и связанный контекст после операций, влияющих на доступ пользователя. */
    private fun refreshRoleContext(session: AuthSession) {
        scope.launch {
            sessionContextService.getRoleContext(session.accessToken).fold(
                onSuccess = { roleContext ->
                    applyRoleContext(
                        session = session,
                        roleContext = roleContext,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            errorMessage = error.message?.take(200) ?: "Не удалось обновить роли пользователя",
                        )
                    }
                },
            )
        }
    }

    /** Встраивает обновленный role context в общую auth-сессию. */
    private fun applyRoleContext(
        session: AuthSession,
        roleContext: com.bam.incomedy.feature.auth.domain.SessionRoleContext,
    ) {
        authViewModel.onIntent(
            AuthIntent.OnRestoreSession(
                session.copy(
                    user = session.user.copy(
                        roles = roleContext.roles,
                        activeRole = roleContext.activeRole,
                        linkedProviders = roleContext.linkedProviders,
                    ),
                ),
            ),
        )
    }
}
