package com.bam.incomedy.shared.session

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision
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

    /** Создает invitation существующему пользователю внутри workspace. */
    fun createWorkspaceInvitation(
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ) {
        viewModel.createWorkspaceInvitation(
            workspaceId = workspaceId,
            inviteeIdentifier = inviteeIdentifier,
            permissionRole = permissionRole,
        )
    }

    /** Принимает pending invitation текущего пользователя. */
    fun acceptWorkspaceInvitation(membershipId: String) {
        viewModel.respondToWorkspaceInvitation(
            membershipId = membershipId,
            decision = WorkspaceInvitationDecision.ACCEPT,
        )
    }

    /** Отклоняет pending invitation текущего пользователя. */
    fun declineWorkspaceInvitation(membershipId: String) {
        viewModel.respondToWorkspaceInvitation(
            membershipId = membershipId,
            decision = WorkspaceInvitationDecision.DECLINE,
        )
    }

    /** Меняет permission role membership внутри workspace. */
    fun updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ) {
        viewModel.updateWorkspaceMembershipRole(
            workspaceId = workspaceId,
            membershipId = membershipId,
            permissionRole = permissionRole,
        )
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
        workspaceInvitations = workspaceInvitations.map(OrganizerWorkspaceInvitation::toSnapshot),
        isLoadingContext = isLoadingContext,
        isUpdatingRole = isUpdatingRole,
        isCreatingWorkspace = isCreatingWorkspace,
        isManagingWorkspaceMembers = isManagingWorkspaceMembers,
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
        canManageMembers = canManageMembers,
        assignablePermissionRoles = assignablePermissionRoles,
        memberships = memberships.map(OrganizerWorkspaceMembership::toSnapshot),
    )
}

/** Преобразует доменную membership-модель в bridge-модель для iOS. */
private fun OrganizerWorkspaceMembership.toSnapshot(): SessionWorkspaceMembershipSnapshot {
    return SessionWorkspaceMembershipSnapshot(
        membershipId = membershipId,
        userId = userId,
        displayName = displayName,
        username = username,
        permissionRole = permissionRole,
        status = status,
        invitedByDisplayName = invitedByDisplayName,
        isCurrentUser = isCurrentUser,
        canEditRole = canEditRole,
        assignablePermissionRoles = assignablePermissionRoles,
    )
}

/** Преобразует pending invitation в bridge-модель для iOS. */
private fun OrganizerWorkspaceInvitation.toSnapshot(): SessionWorkspaceInvitationSnapshot {
    return SessionWorkspaceInvitationSnapshot(
        membershipId = membershipId,
        workspaceId = workspaceId,
        workspaceName = workspaceName,
        workspaceSlug = workspaceSlug,
        workspaceStatus = workspaceStatus,
        permissionRole = permissionRole,
        invitedByDisplayName = invitedByDisplayName,
    )
}

/** Преобразует enum провайдера в стабильный строковый ключ для iOS. */
private fun AuthProviderType.toKey(): String {
    return when (this) {
        AuthProviderType.PASSWORD -> "password"
        AuthProviderType.PHONE -> "phone"
        AuthProviderType.VK -> "vk"
        AuthProviderType.TELEGRAM -> "telegram"
        AuthProviderType.GOOGLE -> "google"
    }
}
