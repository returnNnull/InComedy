package com.bam.incomedy.data.session.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.domain.session.SessionRoleContext
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент post-auth session context API: роли, active role и organizer workspaces.
 *
 * Клиент отделен от auth transport, чтобы логика после успешного входа могла расти
 * независимо от provider-specific auth flows.
 *
 * @property baseUrl Базовый URL backend API.
 * @property parser JSON-парсер для DTO и fallback-ошибок.
 * @property httpClient Настроенный Ktor-клиент для organizer/session-context запросов.
 */
class SessionBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {

    /** Загружает роли, активную роль и привязанные способы входа текущего пользователя. */
    suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/me/roles") {
                    bearer(accessToken)
                }
            ensureBackendSuccess(response, parser)
            response.body<MeRolesResponse>().toDomain()
        }
    }

    /** Переключает активную роль текущего пользователя. */
    suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/me/active-role") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(SetActiveRoleRequest(role = role))
                }
            ensureBackendSuccess(response, parser)
            response.body<MeRolesResponse>().toDomain()
        }
    }

    /** Возвращает список рабочих пространств, доступных текущей сессии. */
    suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/workspaces") {
                    bearer(accessToken)
                }
            ensureBackendSuccess(response, parser)
            response.body<WorkspaceListResponse>().workspaces.map(WorkspaceResponse::toDomain)
        }
    }

    /** Создает рабочее пространство организатора и возвращает его серверное представление. */
    suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String? = null,
    ): Result<OrganizerWorkspace> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/workspaces") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(CreateWorkspaceRequest(name = name, slug = slug))
                }
            ensureBackendSuccess(response, parser)
            response.body<WorkspaceResponse>().toDomain()
        }
    }

    /** Возвращает pending invitations текущего пользователя. */
    suspend fun listWorkspaceInvitations(accessToken: String): Result<List<OrganizerWorkspaceInvitation>> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/workspace-invitations") {
                    bearer(accessToken)
                }
            ensureBackendSuccess(response, parser)
            response.body<WorkspaceInvitationListResponse>().invitations.map(WorkspaceInvitationResponse::toDomain)
        }
    }

    /** Создает invitation существующему пользователю внутри workspace. */
    suspend fun createWorkspaceInvitation(
        accessToken: String,
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/workspaces/$workspaceId/invitations") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateWorkspaceInvitationRequest(
                            inviteeIdentifier = inviteeIdentifier,
                            permissionRole = permissionRole,
                        ),
                    )
                }
            ensureBackendSuccess(response, parser)
            response.body<WorkspaceMembershipResponse>().toDomain()
        }
    }

    /** Передает backend-у решение invitee по pending invitation. */
    suspend fun respondToWorkspaceInvitation(
        accessToken: String,
        membershipId: String,
        decision: WorkspaceInvitationDecision,
    ): Result<Unit> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/workspace-invitations/$membershipId/respond") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(RespondWorkspaceInvitationRequest(decision = decision.wireName))
                }
            ensureBackendSuccess(response, parser)
            Unit
        }
    }

    /** Меняет permission role у указанной membership записи workspace. */
    suspend fun updateWorkspaceMembershipRole(
        accessToken: String,
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ): Result<OrganizerWorkspaceMembership> {
        return runCatching {
            val response = httpClient
                .patch("$baseUrl/api/v1/workspaces/$workspaceId/memberships/$membershipId") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(UpdateWorkspaceMembershipRoleRequest(permissionRole = permissionRole))
                }
            ensureBackendSuccess(response, parser)
            response.body<WorkspaceMembershipResponse>().toDomain()
        }
    }
}

/**
 * DTO ответа с role context пользователя.
 *
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Привязанные способы входа.
 */
@Serializable
private data class MeRolesResponse(
    val roles: List<String>,
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
) {
    /** Преобразует role-context ответ в доменную модель. */
    fun toDomain(): SessionRoleContext {
        return SessionRoleContext(
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }
}

/** DTO запроса смены активной роли. */
@Serializable
private data class SetActiveRoleRequest(
    val role: String,
)

/** DTO списка рабочих пространств. */
@Serializable
private data class WorkspaceListResponse(
    val workspaces: List<WorkspaceResponse>,
)

/** DTO запроса на создание рабочего пространства. */
@Serializable
private data class CreateWorkspaceRequest(
    val name: String,
    val slug: String? = null,
)

/** DTO запроса на создание pending invitation. */
@Serializable
private data class CreateWorkspaceInvitationRequest(
    @SerialName("invitee_identifier")
    val inviteeIdentifier: String,
    @SerialName("permission_role")
    val permissionRole: String,
)

/** DTO запроса на update membership role. */
@Serializable
private data class UpdateWorkspaceMembershipRoleRequest(
    @SerialName("permission_role")
    val permissionRole: String,
)

/** DTO запроса accept/decline invitation. */
@Serializable
private data class RespondWorkspaceInvitationRequest(
    val decision: String,
)

/**
 * DTO рабочего пространства в backend-ответах.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль доступа пользователя внутри рабочего пространства.
 * @property canManageMembers Показывает, может ли viewer управлять командой workspace.
 * @property assignablePermissionRoles Роли, которые viewer может назначать при создании invite.
 * @property memberships Active и pending membership записи workspace.
 */
@Serializable
private data class WorkspaceResponse(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    @SerialName("permission_role")
    val permissionRole: String,
    @SerialName("can_manage_members")
    val canManageMembers: Boolean = false,
    @SerialName("assignable_permission_roles")
    val assignablePermissionRoles: List<String> = emptyList(),
    val memberships: List<WorkspaceMembershipResponse> = emptyList(),
) {
    /** Преобразует DTO рабочего пространства в доменную модель. */
    fun toDomain(): OrganizerWorkspace {
        return OrganizerWorkspace(
            id = id,
            name = name,
            slug = slug,
            status = status,
            permissionRole = permissionRole,
            canManageMembers = canManageMembers,
            assignablePermissionRoles = assignablePermissionRoles,
            memberships = memberships.map(WorkspaceMembershipResponse::toDomain),
        )
    }
}

/**
 * DTO membership внутри organizer workspace.
 *
 * @property membershipId Идентификатор membership/invitation записи.
 * @property userId Идентификатор пользователя, которому принадлежит membership.
 * @property displayName Отображаемое имя участника.
 * @property username Username участника, если он известен.
 * @property permissionRole Permission role внутри workspace.
 * @property status Состояние membership.
 * @property invitedByDisplayName Имя инициатора invite.
 * @property isCurrentUser Показывает, что membership принадлежит текущей сессии.
 * @property canEditRole Показывает, можно ли менять role этой записи.
 * @property assignablePermissionRoles Список ролей, которые можно назначить этой записи.
 */
@Serializable
private data class WorkspaceMembershipResponse(
    @SerialName("membership_id")
    val membershipId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("permission_role")
    val permissionRole: String,
    val status: String,
    @SerialName("invited_by_display_name")
    val invitedByDisplayName: String? = null,
    @SerialName("is_current_user")
    val isCurrentUser: Boolean = false,
    @SerialName("can_edit_role")
    val canEditRole: Boolean = false,
    @SerialName("assignable_permission_roles")
    val assignablePermissionRoles: List<String> = emptyList(),
) {
    /** Преобразует DTO membership в доменную модель. */
    fun toDomain(): OrganizerWorkspaceMembership {
        return OrganizerWorkspaceMembership(
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
}

/** DTO списка pending invitations текущего пользователя. */
@Serializable
private data class WorkspaceInvitationListResponse(
    val invitations: List<WorkspaceInvitationResponse>,
)

/**
 * DTO pending invitation текущего пользователя.
 *
 * @property membershipId Идентификатор invitation записи.
 * @property workspaceId Идентификатор workspace.
 * @property workspaceName Название workspace.
 * @property workspaceSlug Публичный slug workspace.
 * @property workspaceStatus Статус workspace.
 * @property permissionRole Предлагаемая permission role.
 * @property invitedByDisplayName Имя инициатора invitation.
 */
@Serializable
private data class WorkspaceInvitationResponse(
    @SerialName("membership_id")
    val membershipId: String,
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("workspace_name")
    val workspaceName: String,
    @SerialName("workspace_slug")
    val workspaceSlug: String,
    @SerialName("workspace_status")
    val workspaceStatus: String,
    @SerialName("permission_role")
    val permissionRole: String,
    @SerialName("invited_by_display_name")
    val invitedByDisplayName: String? = null,
) {
    /** Преобразует DTO pending invitation в доменную модель. */
    fun toDomain(): OrganizerWorkspaceInvitation {
        return OrganizerWorkspaceInvitation(
            membershipId = membershipId,
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            workspaceSlug = workspaceSlug,
            workspaceStatus = workspaceStatus,
            permissionRole = permissionRole,
            invitedByDisplayName = invitedByDisplayName,
        )
    }
}
