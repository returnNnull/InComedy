package com.bam.incomedy.domain.session

/**
 * Контракт доступа к post-auth контексту авторизованной сессии:
 * ролям, активной роли и organizer workspaces.
 *
 * Интерфейс живет отдельно от auth orchestration, чтобы бизнес-логика после
 * входа не разрасталась внутри auth-слоя и могла эволюционировать своим bounded context.
 */
interface SessionContextService {
    /** Возвращает роли пользователя, активную роль и привязанные провайдеры. */
    suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext>

    /** Переключает активную роль пользователя. */
    suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext>

    /** Возвращает рабочие пространства текущего пользователя. */
    suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>>

    /** Создает рабочее пространство текущего пользователя. */
    suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String? = null,
    ): Result<OrganizerWorkspace>

    /** Возвращает pending invitations текущего пользователя в organizer workspaces. */
    suspend fun listWorkspaceInvitations(accessToken: String): Result<List<OrganizerWorkspaceInvitation>>

    /** Создает invitation существующему пользователю внутри указанного workspace. */
    suspend fun createWorkspaceInvitation(
        accessToken: String,
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership>

    /** Принимает или отклоняет pending invitation текущего пользователя. */
    suspend fun respondToWorkspaceInvitation(
        accessToken: String,
        membershipId: String,
        decision: WorkspaceInvitationDecision,
    ): Result<Unit>

    /** Меняет permission role у active/pending membership указанного workspace. */
    suspend fun updateWorkspaceMembershipRole(
        accessToken: String,
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership>
}

/**
 * Контекст ролей текущей сессии.
 *
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Привязанные auth-провайдеры.
 */
data class SessionRoleContext(
    val roles: List<String>,
    val activeRole: String? = null,
    val linkedProviders: List<String> = emptyList(),
)

/**
 * Рабочее пространство организатора, доступное в текущей сессии.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль пользователя в этом рабочем пространстве.
 * @property canManageMembers Показывает, может ли текущий viewer управлять командой workspace.
 * @property assignablePermissionRoles Список ролей, которые viewer может назначать в invite flow.
 * @property memberships Active и pending membership записи этого workspace.
 */
data class OrganizerWorkspace(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val permissionRole: String,
    val canManageMembers: Boolean = false,
    val assignablePermissionRoles: List<String> = emptyList(),
    val memberships: List<OrganizerWorkspaceMembership> = emptyList(),
)

/**
 * Membership пользователя внутри organizer workspace.
 *
 * @property membershipId Идентификатор membership/invitation записи.
 * @property userId Идентификатор пользователя, которому принадлежит membership.
 * @property displayName Отображаемое имя участника.
 * @property username Username участника, если он известен.
 * @property permissionRole Permission role внутри workspace.
 * @property status Состояние membership: `active` или `invited`.
 * @property invitedByDisplayName Имя пользователя, создавшего invite, если membership pending.
 * @property isCurrentUser Показывает, что membership принадлежит текущей сессии.
 * @property canEditRole Показывает, разрешено ли viewer менять role у этой записи.
 * @property assignablePermissionRoles Список ролей, которые можно назначить этой записи из текущего UI.
 */
data class OrganizerWorkspaceMembership(
    val membershipId: String,
    val userId: String,
    val displayName: String,
    val username: String? = null,
    val permissionRole: String,
    val status: String,
    val invitedByDisplayName: String? = null,
    val isCurrentUser: Boolean = false,
    val canEditRole: Boolean = false,
    val assignablePermissionRoles: List<String> = emptyList(),
)

/**
 * Pending invitation текущего пользователя в organizer workspace.
 *
 * @property membershipId Идентификатор invitation записи.
 * @property workspaceId Идентификатор рабочего пространства.
 * @property workspaceName Название рабочего пространства.
 * @property workspaceSlug Публичный slug рабочего пространства.
 * @property workspaceStatus Статус рабочего пространства.
 * @property permissionRole Роль, с которой пользователя приглашают.
 * @property invitedByDisplayName Отображаемое имя инициатора приглашения.
 */
data class OrganizerWorkspaceInvitation(
    val membershipId: String,
    val workspaceId: String,
    val workspaceName: String,
    val workspaceSlug: String,
    val workspaceStatus: String,
    val permissionRole: String,
    val invitedByDisplayName: String? = null,
)

/**
 * Решение invitee по pending invitation.
 *
 * @property wireName Wire-значение, которое отправляется в backend API.
 */
enum class WorkspaceInvitationDecision(
    val wireName: String,
) {
    ACCEPT("accept"),
    DECLINE("decline"),
}
