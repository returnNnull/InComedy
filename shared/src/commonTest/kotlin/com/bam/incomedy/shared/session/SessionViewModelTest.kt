package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.AuthorizedUser
import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace
import com.bam.incomedy.feature.auth.domain.SessionContextService
import com.bam.incomedy.feature.auth.domain.SessionRoleContext
import com.bam.incomedy.feature.auth.domain.SessionTerminationService
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.domain.ValidatedSession
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
        assertEquals(1, contextService.listWorkspacesCalls)
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

    /** Создает auth `ViewModel` с тестовыми зависимостями. */
    private fun createAuthViewModel(dispatcher: TestDispatcher): AuthViewModel {
        return AuthViewModel(
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

/**
 * Фейковая реализация `SessionContextService`, которая считает вызовы и возвращает
 * детерминированные роли/рабочие пространства.
 *
 * @property roleContext Контекст ролей, который будут возвращать role-методы.
 * @property workspaces Рабочие пространства, которые вернет list.
 * @property createdWorkspace Рабочее пространство, которое вернет create.
 */
private class FakeSessionContextService(
    private val roleContext: SessionRoleContext = SessionRoleContext(
        roles = listOf("audience", "organizer"),
        activeRole = "audience",
        linkedProviders = listOf("telegram"),
    ),
    private val workspaces: List<OrganizerWorkspace> = emptyList(),
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

    /** Последняя роль, которую запросили сделать активной. */
    var lastRequestedRole: String? = null

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
}
