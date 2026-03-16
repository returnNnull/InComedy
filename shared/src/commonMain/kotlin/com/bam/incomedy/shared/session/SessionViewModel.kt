package com.bam.incomedy.shared.session

import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.session.SessionContextService
import com.bam.incomedy.domain.session.SessionRoleContext
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision
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
 * Общая модель сессии, которая синхронизирует auth-состояние с ролями, workspace roster и invitation inbox.
 *
 * @property authViewModel Общая модель авторизации, выступающая источником токенов и базовой сессии.
 * @property sessionContextService Сервис ролей, рабочих пространств и membership flows.
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

    /** Последний access token, для которого уже был инициирован organizer context запрос. */
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
                        workspaceInvitations = if (tokenChanged) emptyList() else current.workspaceInvitations,
                        isLoadingContext = if (tokenChanged) true else current.isLoadingContext,
                        isUpdatingRole = false,
                        isCreatingWorkspace = false,
                        isManagingWorkspaceMembers = false,
                    )
                }
                if (tokenChanged) {
                    refreshOrganizerContext(
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

    /** Создает новое рабочее пространство и обновляет organizer context. */
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
                onSuccess = {
                    _state.update { it.copy(isCreatingWorkspace = false, errorMessage = null) }
                    refreshRoleContext(currentSession)
                    refreshOrganizerContext(
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

    /** Создает invitation существующему пользователю внутри workspace. */
    fun createWorkspaceInvitation(
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ) {
        val currentSession = authViewModel.state.value.session ?: return
        val normalizedWorkspaceId = workspaceId.trim()
        val normalizedInviteeIdentifier = inviteeIdentifier.trim()
        val normalizedPermissionRole = permissionRole.trim().lowercase()
        if (normalizedWorkspaceId.isBlank() || normalizedInviteeIdentifier.length !in 3..80 || normalizedPermissionRole.isBlank()) {
            _state.update { it.copy(errorMessage = "Некорректные данные приглашения в рабочее пространство") }
            return
        }

        scope.launch {
            _state.update { it.copy(isManagingWorkspaceMembers = true, errorMessage = null) }
            sessionContextService.createWorkspaceInvitation(
                accessToken = currentSession.accessToken,
                workspaceId = normalizedWorkspaceId,
                inviteeIdentifier = normalizedInviteeIdentifier,
                permissionRole = normalizedPermissionRole,
            ).fold(
                onSuccess = {
                    _state.update { it.copy(isManagingWorkspaceMembers = false, errorMessage = null) }
                    refreshOrganizerContext(
                        accessToken = currentSession.accessToken,
                        showLoadingState = false,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isManagingWorkspaceMembers = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось отправить приглашение",
                        )
                    }
                },
            )
        }
    }

    /** Принимает или отклоняет pending invitation текущего пользователя. */
    fun respondToWorkspaceInvitation(
        membershipId: String,
        decision: WorkspaceInvitationDecision,
    ) {
        val currentSession = authViewModel.state.value.session ?: return
        val normalizedMembershipId = membershipId.trim()
        if (normalizedMembershipId.isBlank()) {
            _state.update { it.copy(errorMessage = "Некорректный идентификатор приглашения") }
            return
        }

        scope.launch {
            _state.update { it.copy(isManagingWorkspaceMembers = true, errorMessage = null) }
            sessionContextService.respondToWorkspaceInvitation(
                accessToken = currentSession.accessToken,
                membershipId = normalizedMembershipId,
                decision = decision,
            ).fold(
                onSuccess = {
                    _state.update { it.copy(isManagingWorkspaceMembers = false, errorMessage = null) }
                    refreshRoleContext(currentSession)
                    refreshOrganizerContext(
                        accessToken = currentSession.accessToken,
                        showLoadingState = false,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isManagingWorkspaceMembers = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось обработать приглашение",
                        )
                    }
                },
            )
        }
    }

    /** Меняет permission role membership внутри workspace. */
    fun updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ) {
        val currentSession = authViewModel.state.value.session ?: return
        val normalizedWorkspaceId = workspaceId.trim()
        val normalizedMembershipId = membershipId.trim()
        val normalizedPermissionRole = permissionRole.trim().lowercase()
        if (normalizedWorkspaceId.isBlank() || normalizedMembershipId.isBlank() || normalizedPermissionRole.isBlank()) {
            _state.update { it.copy(errorMessage = "Некорректные данные участника рабочего пространства") }
            return
        }

        scope.launch {
            _state.update { it.copy(isManagingWorkspaceMembers = true, errorMessage = null) }
            sessionContextService.updateWorkspaceMembershipRole(
                accessToken = currentSession.accessToken,
                workspaceId = normalizedWorkspaceId,
                membershipId = normalizedMembershipId,
                permissionRole = normalizedPermissionRole,
            ).fold(
                onSuccess = {
                    _state.update { it.copy(isManagingWorkspaceMembers = false, errorMessage = null) }
                    refreshOrganizerContext(
                        accessToken = currentSession.accessToken,
                        showLoadingState = false,
                    )
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isManagingWorkspaceMembers = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось обновить роль участника",
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

    /** Обновляет organizer context: список workspaces и inbox pending invitations. */
    private fun refreshOrganizerContext(
        accessToken: String,
        showLoadingState: Boolean,
    ) {
        scope.launch {
            if (showLoadingState) {
                _state.update { it.copy(isLoadingContext = true, errorMessage = null) }
            }
            var nextWorkspaces: List<OrganizerWorkspace>? = null
            var nextInvitations: List<OrganizerWorkspaceInvitation>? = null
            var nextErrorMessage: String? = null

            sessionContextService.listWorkspaces(accessToken).fold(
                onSuccess = { workspaces ->
                    nextWorkspaces = workspaces
                },
                onFailure = { error ->
                    nextErrorMessage = error.message?.take(200) ?: "Не удалось загрузить рабочие пространства"
                },
            )

            sessionContextService.listWorkspaceInvitations(accessToken).fold(
                onSuccess = { invitations ->
                    nextInvitations = invitations
                },
                onFailure = { error ->
                    if (nextErrorMessage == null) {
                        nextErrorMessage = error.message?.take(200) ?: "Не удалось загрузить приглашения в рабочие пространства"
                    }
                },
            )

            _state.update { current ->
                current.copy(
                    workspaces = nextWorkspaces ?: current.workspaces,
                    workspaceInvitations = nextInvitations ?: current.workspaceInvitations,
                    isLoadingContext = false,
                    errorMessage = nextErrorMessage,
                )
            }
        }
    }

    /** Перезагружает роли и связанный auth context после операций, влияющих на доступ пользователя. */
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
        roleContext: SessionRoleContext,
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
