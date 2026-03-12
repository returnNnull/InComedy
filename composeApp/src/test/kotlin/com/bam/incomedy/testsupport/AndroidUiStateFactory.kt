package com.bam.incomedy.testsupport

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.AuthorizedUser
import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.shared.session.SessionState

/**
 * Фабрика стабильных тестовых состояний для Android UI-тестов.
 */
object AndroidUiStateFactory {

    /**
     * Возвращает типовое состояние авторизованной сессии для post-auth UI-тестов.
     */
    fun sessionState(
        isAuthorized: Boolean = true,
        provider: AuthProviderType = AuthProviderType.TELEGRAM,
        accessToken: String? = "access-token",
        refreshToken: String? = "refresh-token",
        userId: String? = "user-1",
        displayName: String? = "Test User",
        username: String? = "test_user",
        photoUrl: String? = null,
        roles: List<String> = listOf("audience", "organizer"),
        activeRole: String? = "audience",
        linkedProviders: List<String> = listOf("telegram"),
        workspaces: List<OrganizerWorkspace> = listOf(workspace()),
        isLoadingContext: Boolean = false,
        isUpdatingRole: Boolean = false,
        isCreatingWorkspace: Boolean = false,
        errorMessage: String? = null,
    ): SessionState {
        return SessionState(
            isAuthorized = isAuthorized,
            provider = provider,
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
            workspaces = workspaces,
            isLoadingContext = isLoadingContext,
            isUpdatingRole = isUpdatingRole,
            isCreatingWorkspace = isCreatingWorkspace,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает состояние авторизации для UI-тестов экрана входа.
     */
    fun authState(
        isLoading: Boolean = false,
        selectedProvider: AuthProviderType? = null,
        errorMessage: String? = null,
        session: AuthSession? = null,
    ): AuthState {
        return AuthState(
            isLoading = isLoading,
            selectedProvider = selectedProvider,
            errorMessage = errorMessage,
            session = session,
        )
    }

    /**
     * Возвращает авторизованную сессию для состояния после успешного входа.
     */
    fun authSession(
        provider: AuthProviderType = AuthProviderType.TELEGRAM,
        userId: String = "user-1",
        accessToken: String = "access-token",
        refreshToken: String? = "refresh-token",
        user: AuthorizedUser = authorizedUser(),
    ): AuthSession {
        return AuthSession(
            provider = provider,
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user,
        )
    }

    /**
     * Возвращает профиль пользователя для тестовых auth/session состояний.
     */
    fun authorizedUser(
        id: String = "user-1",
        displayName: String = "Test User",
        username: String? = "test_user",
        photoUrl: String? = null,
        roles: List<String> = listOf("audience", "organizer"),
        activeRole: String? = "audience",
        linkedProviders: List<String> = listOf("telegram"),
    ): AuthorizedUser {
        return AuthorizedUser(
            id = id,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }

    /**
     * Возвращает рабочее пространство организатора для тестов главного экрана.
     */
    fun workspace(
        id: String = "ws-1",
        name: String = "Moscow Cellar",
        slug: String = "moscow-cellar",
        status: String = "active",
        permissionRole: String = "owner",
    ): OrganizerWorkspace {
        return OrganizerWorkspace(
            id = id,
            name = name,
            slug = slug,
            status = status,
            permissionRole = permissionRole,
        )
    }
}
