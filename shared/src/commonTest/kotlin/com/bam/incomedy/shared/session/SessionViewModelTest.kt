package com.bam.incomedy.shared.session

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.AuthorizedUser
import com.bam.incomedy.domain.auth.CredentialAuthService
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.domain.session.SessionContextService
import com.bam.incomedy.domain.session.SessionRoleContext
import com.bam.incomedy.domain.auth.SessionTerminationService
import com.bam.incomedy.domain.auth.SessionValidationService
import com.bam.incomedy.domain.auth.SocialAuthService
import com.bam.incomedy.domain.auth.ValidatedSession
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты общей модели сессии для ролей и рабочих пространств.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    /** Проверяет, что восстановленная сессия загружает роли и список рабочих пространств. */
    @Test
    fun `restored session exposes roles and loads workspaces`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextService = FakeSessionContextService(
            workspaces = listOf(
                OrganizerWorkspace(
                    id = "ws-1",
                    name = "Moscow Cellar",
                    slug = "moscow-cellar",
                    status = "active",
                    permissionRole = "owner",
                ),
            ),
            workspaceInvitations = listOf(
                OrganizerWorkspaceInvitation(
                    membershipId = "invite-1",
                    workspaceId = "ws-2",
                    workspaceName = "Late Night Standup",
                    workspaceSlug = "late-night-standup",
                    workspaceStatus = "active",
                    permissionRole = "checker",
                    invitedByDisplayName = "Owner User",
                ),
            ),
        )
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(AuthIntent.OnRestoreSession(baseSession()))
        advanceUntilIdle()

        val state = sessionViewModel.state.value
        assertTrue(state.isAuthorized)
        assertEquals(listOf("audience", "organizer"), state.roles)
        assertEquals("audience", state.activeRole)
        assertEquals(listOf("telegram"), state.linkedProviders)
        assertEquals(1, state.workspaces.size)
        assertEquals("Moscow Cellar", state.workspaces.first().name)
        assertEquals(1, state.workspaceInvitations.size)
        assertEquals("Late Night Standup", state.workspaceInvitations.first().workspaceName)
        assertEquals(1, contextService.listWorkspacesCalls)
        assertEquals(1, contextService.listWorkspaceInvitationsCalls)
    }

    /** Проверяет, что смена роли обновляет и локальное состояние, и общую auth-сессию. */
    @Test
    fun `set active role updates shared session state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextService = FakeSessionContextService(
            roleContext = SessionRoleContext(
                roles = listOf("audience", "organizer"),
                activeRole = "organizer",
                linkedProviders = listOf("telegram"),
            ),
        )
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(AuthIntent.OnRestoreSession(baseSession()))
        advanceUntilIdle()

        sessionViewModel.setActiveRole("organizer")
        advanceUntilIdle()

        assertEquals("organizer", sessionViewModel.state.value.activeRole)
        assertEquals("organizer", authViewModel.state.value.session?.user?.activeRole)
        assertEquals(1, contextService.setActiveRoleCalls)
        assertEquals("organizer", contextService.lastRequestedRole)
    }

    /** Проверяет, что создание рабочего пространства обновляет роли и повторно загружает список. */
    @Test
    fun `create workspace refreshes role context and workspace list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val createdWorkspace = OrganizerWorkspace(
            id = "ws-2",
            name = "Late Night Standup",
            slug = "late-night-standup",
            status = "active",
            permissionRole = "owner",
        )
        val contextService = FakeSessionContextService(
            roleContext = SessionRoleContext(
                roles = listOf("audience", "organizer"),
                activeRole = "organizer",
                linkedProviders = listOf("telegram"),
            ),
            workspaces = listOf(createdWorkspace),
            createdWorkspace = createdWorkspace,
        )
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(
            AuthIntent.OnRestoreSession(
                baseSession(
                    user = AuthorizedUser(
                        id = "user-1",
                        displayName = "Test User",
                        roles = listOf("audience"),
                        activeRole = "audience",
                        linkedProviders = listOf("telegram"),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        sessionViewModel.createWorkspace(name = "Late Night Standup")
        advanceUntilIdle()

        val state = sessionViewModel.state.value
        assertEquals("organizer", state.activeRole)
        assertEquals(listOf("audience", "organizer"), state.roles)
        assertEquals(1, state.workspaces.size)
        assertEquals("Late Night Standup", state.workspaces.first().name)
        assertEquals(1, contextService.createWorkspaceCalls)
        assertTrue(contextService.getRoleContextCalls >= 1)
        assertTrue(contextService.listWorkspacesCalls >= 2)
    }

    /** Проверяет, что invitation mutation обновляет organizer context. */
    @Test
    fun `create workspace invitation refreshes organizer context`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextService = FakeSessionContextService(
            workspaces = listOf(
                OrganizerWorkspace(
                    id = "ws-1",
                    name = "Moscow Cellar",
                    slug = "moscow-cellar",
                    status = "active",
                    permissionRole = "owner",
                    canManageMembers = true,
                    assignablePermissionRoles = listOf("manager", "checker", "host"),
                    memberships = listOf(
                        OrganizerWorkspaceMembership(
                            membershipId = "member-1",
                            userId = "user-1",
                            displayName = "Owner User",
                            username = "owner",
                            permissionRole = "owner",
                            status = "active",
                        ),
                    ),
                ),
            ),
            workspaceInvitations = listOf(
                OrganizerWorkspaceInvitation(
                    membershipId = "invite-1",
                    workspaceId = "ws-2",
                    workspaceName = "Late Night Standup",
                    workspaceSlug = "late-night-standup",
                    workspaceStatus = "active",
                    permissionRole = "checker",
                    invitedByDisplayName = "Owner User",
                ),
            ),
        )
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(AuthIntent.OnRestoreSession(baseSession()))
        advanceUntilIdle()

        sessionViewModel.createWorkspaceInvitation(
            workspaceId = "ws-1",
            inviteeIdentifier = "checker_user",
            permissionRole = "checker",
        )
        advanceUntilIdle()

        assertEquals(1, contextService.createWorkspaceInvitationCalls)
        assertEquals("checker_user", contextService.lastInviteeIdentifier)
        assertEquals("checker", contextService.lastRequestedWorkspacePermissionRole)
        assertTrue(contextService.listWorkspacesCalls >= 2)
        assertTrue(contextService.listWorkspaceInvitationsCalls >= 2)
    }

    /** Проверяет, что accept invitation обновляет роль organizer и очищает inbox. */
    @Test
    fun `responding to workspace invitation refreshes roles and invitations`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextService = FakeSessionContextService(
            roleContext = SessionRoleContext(
                roles = listOf("audience", "organizer"),
                activeRole = "audience",
                linkedProviders = listOf("telegram"),
            ),
        )
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(
            AuthIntent.OnRestoreSession(
                baseSession(
                    user = AuthorizedUser(
                        id = "user-1",
                        displayName = "Invitee",
                        roles = listOf("audience"),
                        activeRole = "audience",
                        linkedProviders = listOf("telegram"),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        sessionViewModel.respondToWorkspaceInvitation(
            membershipId = "invite-1",
            decision = WorkspaceInvitationDecision.ACCEPT,
        )
        advanceUntilIdle()

        assertEquals(1, contextService.respondToWorkspaceInvitationCalls)
        assertEquals(WorkspaceInvitationDecision.ACCEPT, contextService.lastInvitationDecision)
        assertEquals(listOf("audience", "organizer"), sessionViewModel.state.value.roles)
        assertTrue(contextService.getRoleContextCalls >= 1)
    }

    /** Проверяет, что membership role update перезагружает organizer context. */
    @Test
    fun `update workspace membership role refreshes organizer context`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val contextService = FakeSessionContextService()
        val authViewModel = createAuthViewModel(dispatcher)
        val sessionViewModel = SessionViewModel(
            authViewModel = authViewModel,
            sessionContextService = contextService,
            dispatcher = dispatcher,
        )

        authViewModel.onIntent(AuthIntent.OnRestoreSession(baseSession()))
        advanceUntilIdle()

        sessionViewModel.updateWorkspaceMembershipRole(
            workspaceId = "ws-1",
            membershipId = "member-2",
            permissionRole = "host",
        )
        advanceUntilIdle()

        assertEquals(1, contextService.updateWorkspaceMembershipRoleCalls)
        assertEquals("member-2", contextService.lastWorkspaceMembershipId)
        assertEquals("host", contextService.lastRequestedWorkspacePermissionRole)
        assertTrue(contextService.listWorkspacesCalls >= 2)
    }

    /** Создает auth `ViewModel` с тестовыми зависимостями. */
    private fun createAuthViewModel(dispatcher: TestDispatcher): AuthViewModel {
        return AuthViewModel(
            credentialAuthService = FakeCredentialAuthService(),
            socialAuthService = SocialAuthService(emptyList()),
            sessionValidationService = FakeSessionValidationService(),
            sessionTerminationService = FakeSessionTerminationService(),
            dispatcher = dispatcher,
        )
    }

    /** Возвращает базовую тестовую сессию пользователя. */
    private fun baseSession(
        user: AuthorizedUser = AuthorizedUser(
            id = "user-1",
            displayName = "Test User",
            roles = listOf("audience", "organizer"),
            activeRole = "audience",
            linkedProviders = listOf("telegram"),
        ),
    ): AuthSession {
        return AuthSession(
            provider = AuthProviderType.TELEGRAM,
            userId = user.id,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = user,
        )
    }
}

/** Фейковая реализация валидации сессии для тестов `SessionViewModel`. */
private class FakeSessionValidationService : SessionValidationService {
    /** Всегда возвращает успешную валидацию текущей тестовой сессии. */
    override suspend fun validate(accessToken: String, refreshToken: String?): Result<ValidatedSession> {
        return Result.success(
            ValidatedSession(
                provider = AuthProviderType.TELEGRAM,
                userId = "user-1",
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = AuthorizedUser(
                    id = "user-1",
                    displayName = "Test User",
                    roles = listOf("audience"),
                    activeRole = "audience",
                    linkedProviders = listOf("telegram"),
                ),
            ),
        )
    }
}

/** Фейковая реализация завершения сессии для тестов `SessionViewModel`. */
private class FakeSessionTerminationService : SessionTerminationService {
    /** Всегда успешно завершает тестовую сессию. */
    override suspend fun terminate(accessToken: String): Result<Unit> {
        return Result.success(Unit)
    }
}

/** Фейковая credential auth реализация для конструирования `AuthViewModel` в session tests. */
private class FakeCredentialAuthService : CredentialAuthService {
    /** Возвращает стабильную password-сессию для тестовой модели. */
    override suspend fun signIn(login: String, password: String): Result<AuthSession> {
        return Result.success(
            AuthSession(
                provider = AuthProviderType.PASSWORD,
                userId = "user-1",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                user = AuthorizedUser(
                    id = "user-1",
                    displayName = login,
                    username = login,
                ),
            ),
        )
    }

    /** Для session tests регистрация эквивалентна успешному sign-in. */
    override suspend fun register(login: String, password: String): Result<AuthSession> {
        return signIn(login = login, password = password)
    }
}

/**
 * Фейковая реализация `SessionContextService`, которая считает вызовы и возвращает
 * детерминированные роли/рабочие пространства.
 *
 * @property roleContext Контекст ролей, который будут возвращать role-методы.
 * @property workspaces Рабочие пространства, которые вернет list.
 * @property workspaceInvitations Pending invitations, которые вернет inbox list.
 * @property createdWorkspace Рабочее пространство, которое вернет create.
 */
private class FakeSessionContextService(
    private val roleContext: SessionRoleContext = SessionRoleContext(
        roles = listOf("audience", "organizer"),
        activeRole = "audience",
        linkedProviders = listOf("telegram"),
    ),
    private val workspaces: List<OrganizerWorkspace> = emptyList(),
    private val workspaceInvitations: List<OrganizerWorkspaceInvitation> = emptyList(),
    private val createdWorkspace: OrganizerWorkspace = OrganizerWorkspace(
        id = "ws-created",
        name = "Created Workspace",
        slug = "created-workspace",
        status = "active",
        permissionRole = "owner",
    ),
) : SessionContextService {
    /** Счетчик вызовов загрузки role context. */
    var getRoleContextCalls: Int = 0

    /** Счетчик вызовов смены активной роли. */
    var setActiveRoleCalls: Int = 0

    /** Счетчик вызовов загрузки рабочих пространств. */
    var listWorkspacesCalls: Int = 0

    /** Счетчик вызовов создания рабочего пространства. */
    var createWorkspaceCalls: Int = 0

    /** Счетчик вызовов загрузки pending invitations. */
    var listWorkspaceInvitationsCalls: Int = 0

    /** Счетчик вызовов создания workspace invitation. */
    var createWorkspaceInvitationCalls: Int = 0

    /** Счетчик вызовов ответа по invitation. */
    var respondToWorkspaceInvitationCalls: Int = 0

    /** Счетчик вызовов update membership role. */
    var updateWorkspaceMembershipRoleCalls: Int = 0

    /** Последняя роль, которую запросили сделать активной. */
    var lastRequestedRole: String? = null

    /** Последний invitee identifier для create invitation. */
    var lastInviteeIdentifier: String? = null

    /** Последний membership id для invitation/member operations. */
    var lastWorkspaceMembershipId: String? = null

    /** Последняя permission role для workspace member operations. */
    var lastRequestedWorkspacePermissionRole: String? = null

    /** Последнее решение invitee по pending invitation. */
    var lastInvitationDecision: WorkspaceInvitationDecision? = null

    /** Возвращает тестовый role context. */
    override suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext> {
        getRoleContextCalls += 1
        return Result.success(roleContext)
    }

    /** Возвращает test role context и запоминает запрос смены роли. */
    override suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext> {
        setActiveRoleCalls += 1
        lastRequestedRole = role
        return Result.success(roleContext.copy(activeRole = role))
    }

    /** Возвращает тестовый список рабочих пространств. */
    override suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>> {
        listWorkspacesCalls += 1
        return Result.success(workspaces)
    }

    /** Возвращает результат создания рабочего пространства для теста. */
    override suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String?,
    ): Result<OrganizerWorkspace> {
        createWorkspaceCalls += 1
        return Result.success(
            createdWorkspace.copy(
                name = name,
                slug = slug ?: createdWorkspace.slug,
            ),
        )
    }

    /** Возвращает pending invitations текущего пользователя. */
    override suspend fun listWorkspaceInvitations(accessToken: String): Result<List<OrganizerWorkspaceInvitation>> {
        listWorkspaceInvitationsCalls += 1
        return Result.success(workspaceInvitations)
    }

    /** Запоминает параметры invitation create flow. */
    override suspend fun createWorkspaceInvitation(
        accessToken: String,
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        createWorkspaceInvitationCalls += 1
        lastInviteeIdentifier = inviteeIdentifier
        lastRequestedWorkspacePermissionRole = permissionRole
        return Result.success(
            OrganizerWorkspaceMembership(
                membershipId = "membership-created",
                userId = "user-2",
                displayName = inviteeIdentifier,
                username = inviteeIdentifier,
                permissionRole = permissionRole,
                status = "invited",
                invitedByDisplayName = "Owner User",
            ),
        )
    }

    /** Запоминает решение invitee по pending invitation. */
    override suspend fun respondToWorkspaceInvitation(
        accessToken: String,
        membershipId: String,
        decision: WorkspaceInvitationDecision,
    ): Result<Unit> {
        respondToWorkspaceInvitationCalls += 1
        lastWorkspaceMembershipId = membershipId
        lastInvitationDecision = decision
        return Result.success(Unit)
    }

    /** Запоминает membership role update и возвращает фиктивный membership. */
    override suspend fun updateWorkspaceMembershipRole(
        accessToken: String,
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        updateWorkspaceMembershipRoleCalls += 1
        lastWorkspaceMembershipId = membershipId
        lastRequestedWorkspacePermissionRole = permissionRole
        return Result.success(
            OrganizerWorkspaceMembership(
                membershipId = membershipId,
                userId = "user-2",
                displayName = "Member User",
                username = "member_user",
                permissionRole = permissionRole,
                status = "active",
                assignablePermissionRoles = listOf("checker", "host"),
            ),
        )
    }
}
