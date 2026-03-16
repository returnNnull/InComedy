package com.bam.incomedy.data.session.backend

import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.domain.session.SessionContextService
import com.bam.incomedy.domain.session.SessionRoleContext
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision

/**
 * Реализация `SessionContextService`, которая проксирует запросы контекста сессии
 * в выделенный backend API post-auth области.
 *
 * @property sessionBackendApi HTTP-клиент ролей и organizer workspace context.
 */
class BackendSessionContextService(
    private val sessionBackendApi: SessionBackendApi,
) : SessionContextService {

    /** Загружает роли, активную роль и привязанные провайдеры пользователя. */
    override suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext> {
        return sessionBackendApi.getRoleContext(accessToken)
    }

    /** Переключает активную роль пользователя. */
    override suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext> {
        return sessionBackendApi.setActiveRole(
            accessToken = accessToken,
            role = role,
        )
    }

    /** Загружает рабочие пространства текущего пользователя. */
    override suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>> {
        return sessionBackendApi.listWorkspaces(accessToken)
    }

    /** Создает новое рабочее пространство для текущего пользователя. */
    override suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String?,
    ): Result<OrganizerWorkspace> {
        return sessionBackendApi.createWorkspace(
            accessToken = accessToken,
            name = name,
            slug = slug,
        )
    }

    /** Загружает pending invitations текущего пользователя. */
    override suspend fun listWorkspaceInvitations(accessToken: String): Result<List<OrganizerWorkspaceInvitation>> {
        return sessionBackendApi.listWorkspaceInvitations(accessToken)
    }

    /** Создает workspace invitation через backend API. */
    override suspend fun createWorkspaceInvitation(
        accessToken: String,
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        return sessionBackendApi.createWorkspaceInvitation(
            accessToken = accessToken,
            workspaceId = workspaceId,
            inviteeIdentifier = inviteeIdentifier,
            permissionRole = permissionRole,
        )
    }

    /** Передает backend-у решение invitee по pending invitation. */
    override suspend fun respondToWorkspaceInvitation(
        accessToken: String,
        membershipId: String,
        decision: WorkspaceInvitationDecision,
    ): Result<Unit> {
        return sessionBackendApi.respondToWorkspaceInvitation(
            accessToken = accessToken,
            membershipId = membershipId,
            decision = decision,
        )
    }

    /** Меняет permission role membership через backend API. */
    override suspend fun updateWorkspaceMembershipRole(
        accessToken: String,
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        return sessionBackendApi.updateWorkspaceMembershipRole(
            accessToken = accessToken,
            workspaceId = workspaceId,
            membershipId = membershipId,
            permissionRole = permissionRole,
        )
    }
}
