package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей моделью сессии, который отдает iOS безопасные снепшоты и команды.
 *
 * @property viewModel Общая модель сессии из KMP-слоя.
 */
class SessionBridge(
    private val viewModel: SessionViewModel = InComedyKoin.getSessionViewModel(),
) : BaseFeatureBridge() {

    /** Возвращает текущее значение сессии единым снимком для Swift-слоя. */
    fun currentState(): SessionStateSnapshot = viewModel.state.value.toSnapshot()

    /** Подписывает Swift-слой на обновления состояния сессии. */
    fun observeState(onState: (SessionStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    /** Выполняет выход текущего пользователя. */
    fun signOut() {
        viewModel.signOut()
    }

    /** Переключает активную роль пользователя. */
    fun setActiveRole(role: String) {
        viewModel.setActiveRole(role)
    }

    /** Создает рабочее пространство из iOS UI. */
    fun createWorkspace(name: String, slug: String?) {
        viewModel.createWorkspace(name = name, slug = slug)
    }

    /** Скрывает текущую ошибку слоя сессии. */
    fun clearError() {
        viewModel.clearError()
    }

    /** Восстанавливает сессию по уже сохраненному access token. */
    fun restoreSessionToken(accessToken: String) {
        viewModel.restoreSessionToken(accessToken)
    }
}

/** Преобразует внутреннее состояние в экспортируемый bridge-снимок. */
private fun SessionState.toSnapshot(): SessionStateSnapshot {
    return SessionStateSnapshot(
        isAuthorized = isAuthorized,
        providerKey = provider?.toKey(),
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
        displayName = displayName,
        username = username,
        photoUrl = photoUrl,
        roles = roles,
        activeRole = activeRole,
        linkedProviders = linkedProviders,
        workspaces = workspaces.map(OrganizerWorkspace::toSnapshot),
        isLoadingContext = isLoadingContext,
        isUpdatingRole = isUpdatingRole,
        isCreatingWorkspace = isCreatingWorkspace,
        errorMessage = errorMessage,
    )
}

/** Преобразует доменное рабочее пространство в bridge-модель для iOS. */
private fun OrganizerWorkspace.toSnapshot(): SessionWorkspaceSnapshot {
    return SessionWorkspaceSnapshot(
        id = id,
        name = name,
        slug = slug,
        status = status,
        permissionRole = permissionRole,
    )
}

/** Преобразует enum провайдера в стабильный строковый ключ для iOS. */
private fun AuthProviderType.toKey(): String {
    return when (this) {
        AuthProviderType.VK -> "vk"
        AuthProviderType.TELEGRAM -> "telegram"
        AuthProviderType.GOOGLE -> "google"
    }
}
