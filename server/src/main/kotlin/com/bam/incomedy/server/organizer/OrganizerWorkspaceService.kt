package com.bam.incomedy.server.organizer

import com.bam.incomedy.server.db.StoredWorkspaceMembership
import com.bam.incomedy.server.db.UserRepository
import com.bam.incomedy.server.db.WorkspacePermissionRole

/**
 * Сервис organizer workspace membership flows.
 *
 * Он инкапсулирует bounded role-policy для invitations и permission updates, чтобы
 * HTTP-роуты не дублировали RBAC-решения по owner/manager/checker/host.
 *
 * @property userRepository Репозиторий пользователей, рабочих пространств и membership записей.
 */
class OrganizerWorkspaceService(
    private val userRepository: UserRepository,
) {
    /** Возвращает рабочие пространства текущего пользователя. */
    fun listWorkspaces(userId: String) = userRepository.listWorkspaces(userId)

    /** Возвращает pending invitations, ожидающие решения текущего пользователя. */
    fun listInvitations(userId: String) = userRepository.listWorkspaceInvitations(userId)

    /**
     * Создает invitation в workspace после проверки permission matrix.
     *
     * - `owner` может выдавать `manager/checker/host`
     * - `manager` может выдавать только `checker/host`
     */
    fun createInvitation(
        actorUserId: String,
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership {
        val access = userRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw WorkspaceScopeNotFoundException(workspaceId)
        if (permissionRole !in assignableRoles(access.permissionRole)) {
            throw WorkspacePermissionDeniedException("role_not_assignable")
        }
        return userRepository.createWorkspaceInvitation(
            workspaceId = workspaceId,
            invitedByUserId = actorUserId,
            inviteeIdentifier = inviteeIdentifier,
            permissionRole = permissionRole,
        )
    }

    /**
     * Обновляет permission role membership с учетом actor role и текущего target role.
     *
     * Сервис блокирует self-edit и любые попытки менять owner membership.
     */
    fun updateMembershipRole(
        actorUserId: String,
        workspaceId: String,
        membershipId: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership {
        val access = userRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw WorkspaceScopeNotFoundException(workspaceId)
        val membership = userRepository.findWorkspaceMembership(
            workspaceId = workspaceId,
            membershipId = membershipId,
        ) ?: throw WorkspaceMembershipNotFoundException(workspaceId, membershipId)
        if (membership.userId == actorUserId) {
            throw WorkspacePermissionDeniedException("self_edit_forbidden")
        }
        val assignableRoles = membershipAssignableRoles(
            actorPermissionRole = access.permissionRole,
            actorUserId = actorUserId,
            membership = membership,
        )
        if (permissionRole !in assignableRoles) {
            throw WorkspacePermissionDeniedException("membership_role_not_assignable")
        }
        return userRepository.updateWorkspaceMembershipRole(
            workspaceId = workspaceId,
            membershipId = membershipId,
            permissionRole = permissionRole,
        ) ?: throw WorkspaceMembershipNotFoundException(workspaceId, membershipId)
    }

    /** Принимает или отклоняет invitation текущего пользователя. */
    fun respondToInvitation(
        userId: String,
        membershipId: String,
        accept: Boolean,
    ) {
        val updated = userRepository.respondToWorkspaceInvitation(
            userId = userId,
            membershipId = membershipId,
            accept = accept,
        )
        if (!updated) {
            throw WorkspaceInvitationNotFoundException(membershipId)
        }
    }

    /** Показывает, может ли текущая workspace role управлять составом команды. */
    fun canManageMembers(permissionRole: WorkspacePermissionRole): Boolean {
        return assignableRoles(permissionRole).isNotEmpty()
    }

    /** Возвращает роли, которые actor может назначать в invite/create flow. */
    fun assignableRoles(permissionRole: WorkspacePermissionRole): List<WorkspacePermissionRole> {
        return when (permissionRole) {
            WorkspacePermissionRole.OWNER -> listOf(
                WorkspacePermissionRole.MANAGER,
                WorkspacePermissionRole.CHECKER,
                WorkspacePermissionRole.HOST,
            )

            WorkspacePermissionRole.MANAGER -> listOf(
                WorkspacePermissionRole.CHECKER,
                WorkspacePermissionRole.HOST,
            )

            WorkspacePermissionRole.CHECKER,
            WorkspacePermissionRole.HOST,
            -> emptyList()
        }
    }

    /**
     * Возвращает допустимые target roles для конкретного membership.
     *
     * Политика остается узкой:
     * - owner редактирует все non-owner memberships
     * - manager редактирует только checker/host memberships
     * - checker/host не редактируют команду
     */
    fun membershipAssignableRoles(
        actorPermissionRole: WorkspacePermissionRole,
        actorUserId: String,
        membership: StoredWorkspaceMembership,
    ): List<WorkspacePermissionRole> {
        if (membership.userId == actorUserId) return emptyList()
        return when (actorPermissionRole) {
            WorkspacePermissionRole.OWNER -> {
                if (membership.permissionRole == WorkspacePermissionRole.OWNER) {
                    emptyList()
                } else {
                    assignableRoles(actorPermissionRole)
                }
            }

            WorkspacePermissionRole.MANAGER -> {
                if (membership.permissionRole == WorkspacePermissionRole.CHECKER ||
                    membership.permissionRole == WorkspacePermissionRole.HOST
                ) {
                    assignableRoles(actorPermissionRole)
                } else {
                    emptyList()
                }
            }

            WorkspacePermissionRole.CHECKER,
            WorkspacePermissionRole.HOST,
            -> emptyList()
        }
    }
}

/** Сигнализирует, что actor не имеет активного доступа к указанному workspace. */
class WorkspaceScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("Workspace scope was not found")

/** Сигнализирует, что target membership не найден внутри workspace. */
class WorkspaceMembershipNotFoundException(
    val workspaceId: String,
    val membershipId: String,
) : IllegalStateException("Workspace membership was not found")

/** Сигнализирует, что invitation не принадлежит текущему пользователю или уже не pending. */
class WorkspaceInvitationNotFoundException(
    val membershipId: String,
) : IllegalStateException("Workspace invitation was not found")

/** Сигнализирует о запрете по bounded workspace permission policy. */
class WorkspacePermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Workspace action is forbidden")
