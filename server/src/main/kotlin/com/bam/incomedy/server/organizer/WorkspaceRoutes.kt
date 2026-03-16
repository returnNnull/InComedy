package com.bam.incomedy.server.organizer

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.StoredWorkspace
import com.bam.incomedy.server.db.StoredWorkspaceInvitation
import com.bam.incomedy.server.db.StoredWorkspaceMembership
import com.bam.incomedy.server.db.WorkspaceInviteeNotFoundException
import com.bam.incomedy.server.db.WorkspaceMembershipAlreadyExistsException
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
import com.bam.incomedy.server.security.AuthRateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Organizer workspace routes для summary, invitations и permission-role management.
 *
 * Роуты работают поверх bounded workspace policy:
 * - `owner` управляет `manager/checker/host`
 * - `manager` управляет только `checker/host`
 * - `checker` и `host` видят roster/read-only.
 */
object WorkspaceRoutes {
    /** Структурированный logger organizer workspace surface. */
    private val logger = LoggerFactory.getLogger(WorkspaceRoutes::class.java)

    /** Строгий JSON parser для organizer payloads. */
    private val requestJson = Json { ignoreUnknownKeys = false }

    /** Максимально допустимый размер тела запроса на создание workspace. */
    private const val MAX_CREATE_WORKSPACE_REQUEST_BYTES = 2 * 1024

    /** Максимально допустимый размер тела invite request. */
    private const val MAX_CREATE_INVITATION_REQUEST_BYTES = 2 * 1024

    /** Максимально допустимый размер тела membership role update request. */
    private const val MAX_UPDATE_MEMBERSHIP_REQUEST_BYTES = 2 * 1024

    /** Максимально допустимый размер тела invitation response request. */
    private const val MAX_RESPOND_INVITATION_REQUEST_BYTES = 1024

    /** Валидация slug рабочего пространства. */
    private val slugRegex = Regex("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$")

    /** Регистрирует organizer workspace routes внутри защищенного session scope. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        sessionUserRepository: SessionUserRepository,
        workspaceRepository: WorkspaceRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        val workspaceService = OrganizerWorkspaceService(workspaceRepository)

        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = sessionUserRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/workspaces") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspaces_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspaces.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }

                    val workspaces = workspaceService.listWorkspaces(principal.user.id).map {
                        it.toResponse(
                            currentUserId = principal.user.id,
                            workspaceService = workspaceService,
                        )
                    }
                    logger.info(
                        "organizer.workspaces.list.success requestId={} userId={} count={}",
                        requestId,
                        principal.user.id,
                        workspaces.size,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "organizer.workspaces.list.success",
                        status = HttpStatusCode.OK.value,
                        metadata = mapOf("count" to workspaces.size.toString()),
                    )
                    call.respond(HttpStatusCode.OK, WorkspaceListResponse(workspaces))
                }

                post("/workspaces") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspace_create:${principal.user.id}", limit = 20, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<CreateWorkspaceRequest>(
                        json = requestJson,
                        maxBytes = MAX_CREATE_WORKSPACE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "organizer.workspace.create.payload_too_large",
                                status = HttpStatusCode.PayloadTooLarge.value,
                                safeErrorCode = "payload_too_large",
                            )
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                ErrorResponse("payload_too_large", "Request body is too large"),
                            )
                            return@post
                        }
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.create.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace request"))
                        return@post
                    }
                    val name = request.name.trim()
                    if (name.length !in 3..80) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.create.invalid_name",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Workspace name must be 3-80 chars"))
                        return@post
                    }
                    val slug = request.slug
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf(String::isNotBlank)
                        ?: generatedSlug(name)
                    if (!slugRegex.matches(slug)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.create.invalid_slug",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace slug"))
                        return@post
                    }
                    val workspace = workspaceRepository.createWorkspace(
                        ownerUserId = principal.user.id,
                        name = name,
                        slug = slug,
                    )
                    logger.info(
                        "organizer.workspace.created requestId={} userId={} workspaceId={}",
                        requestId,
                        principal.user.id,
                        workspace.id,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "organizer.workspace.create.success",
                        status = HttpStatusCode.Created.value,
                    )
                    call.respond(
                        HttpStatusCode.Created,
                        workspace.toResponse(
                            currentUserId = principal.user.id,
                            workspaceService = workspaceService,
                        ),
                    )
                }

                get("/workspace-invitations") {
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspace_invitations_list:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace_invitations.list.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }
                    val invitations = workspaceService.listInvitations(principal.user.id).map(StoredWorkspaceInvitation::toResponse)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "organizer.workspace_invitations.list.success",
                        status = HttpStatusCode.OK.value,
                        metadata = mapOf("count" to invitations.size.toString()),
                    )
                    call.respond(HttpStatusCode.OK, WorkspaceInvitationListResponse(invitations))
                }

                post("/workspaces/{workspaceId}/invitations") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspace_invitation_create:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val workspaceId = call.parameters["workspaceId"]
                    if (!workspaceId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.invalid_workspace_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace id"))
                        return@post
                    }
                    val resolvedWorkspaceId = workspaceId.orEmpty()
                    val request = call.receiveJsonBodyLimited<CreateWorkspaceInvitationRequest>(
                        json = requestJson,
                        maxBytes = MAX_CREATE_INVITATION_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "organizer.workspace.invitation.create.payload_too_large",
                                status = HttpStatusCode.PayloadTooLarge.value,
                                safeErrorCode = "payload_too_large",
                            )
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                ErrorResponse("payload_too_large", "Request body is too large"),
                            )
                            return@post
                        }
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace invitation request"))
                        return@post
                    }
                    val inviteeIdentifier = request.inviteeIdentifier.trim()
                    if (inviteeIdentifier.length !in 3..80) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.invalid_invitee_identifier",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invitee identifier must be 3-80 chars"))
                        return@post
                    }
                    val permissionRole = WorkspacePermissionRole.fromWireName(request.permissionRole)
                    if (permissionRole == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.invalid_permission_role",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown workspace permission role"))
                        return@post
                    }
                    try {
                        val invitation = workspaceService.createInvitation(
                            actorUserId = principal.user.id,
                            workspaceId = resolvedWorkspaceId,
                            inviteeIdentifier = inviteeIdentifier,
                            permissionRole = permissionRole,
                        )
                        logger.info(
                            "organizer.workspace.invitation.create.success requestId={} userId={} workspaceId={} membershipId={} role={}",
                            requestId,
                            principal.user.id,
                            workspaceId,
                            invitation.membershipId,
                            permissionRole.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.success",
                            status = HttpStatusCode.Created.value,
                            metadata = mapOf("permissionRole" to permissionRole.wireName),
                        )
                        call.respond(
                            HttpStatusCode.Created,
                            invitation.toResponse(
                                currentUserId = principal.user.id,
                                workspaceService = workspaceService,
                                viewerPermissionRole = workspaceRepository.findWorkspaceAccess(
                                    workspaceId = resolvedWorkspaceId,
                                    userId = principal.user.id,
                                )?.permissionRole ?: WorkspacePermissionRole.OWNER,
                            ),
                        )
                    } catch (error: WorkspaceScopeNotFoundException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.workspace_not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "workspace_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("workspace_not_found", "Workspace not found"))
                    } catch (error: WorkspacePermissionDeniedException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.forbidden",
                            status = HttpStatusCode.Forbidden.value,
                            safeErrorCode = error.reasonCode,
                        )
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Workspace invitation is forbidden"))
                    } catch (error: WorkspaceInviteeNotFoundException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.invitee_not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "invitee_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("invitee_not_found", "Invitee was not found"))
                    } catch (error: WorkspaceMembershipAlreadyExistsException) {
                        val errorCode = if (error.pending) "invitation_already_exists" else "member_already_exists"
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.create.conflict",
                            status = HttpStatusCode.Conflict.value,
                            safeErrorCode = errorCode,
                        )
                        call.respond(HttpStatusCode.Conflict, ErrorResponse(errorCode, "Workspace membership already exists"))
                    }
                }

                patch("/workspaces/{workspaceId}/memberships/{membershipId}") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspace_membership_update:${principal.user.id}", limit = 40, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@patch
                    }
                    val workspaceId = call.parameters["workspaceId"]
                    val membershipId = call.parameters["membershipId"]
                    if (!workspaceId.isUuid() || !membershipId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.invalid_path",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace membership path"))
                        return@patch
                    }
                    val resolvedWorkspaceId = workspaceId.orEmpty()
                    val resolvedMembershipId = membershipId.orEmpty()
                    val request = call.receiveJsonBodyLimited<UpdateWorkspaceMembershipRoleRequest>(
                        json = requestJson,
                        maxBytes = MAX_UPDATE_MEMBERSHIP_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "organizer.workspace.membership.update.payload_too_large",
                                status = HttpStatusCode.PayloadTooLarge.value,
                                safeErrorCode = "payload_too_large",
                            )
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                ErrorResponse("payload_too_large", "Request body is too large"),
                            )
                            return@patch
                        }
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace membership update request"))
                        return@patch
                    }
                    val permissionRole = WorkspacePermissionRole.fromWireName(request.permissionRole)
                    if (permissionRole == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.invalid_permission_role",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown workspace permission role"))
                        return@patch
                    }
                    try {
                        val membership = workspaceService.updateMembershipRole(
                            actorUserId = principal.user.id,
                            workspaceId = resolvedWorkspaceId,
                            membershipId = resolvedMembershipId,
                            permissionRole = permissionRole,
                        )
                        logger.info(
                            "organizer.workspace.membership.update.success requestId={} userId={} workspaceId={} membershipId={} role={}",
                            requestId,
                            principal.user.id,
                            workspaceId,
                            membershipId,
                            permissionRole.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("permissionRole" to permissionRole.wireName),
                        )
                        val viewerPermissionRole = workspaceRepository.findWorkspaceAccess(
                            workspaceId = resolvedWorkspaceId,
                            userId = principal.user.id,
                        )?.permissionRole ?: permissionRole
                        call.respond(
                            HttpStatusCode.OK,
                            membership.toResponse(
                                currentUserId = principal.user.id,
                                workspaceService = workspaceService,
                                viewerPermissionRole = viewerPermissionRole,
                            ),
                        )
                    } catch (error: WorkspaceScopeNotFoundException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.workspace_not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "workspace_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("workspace_not_found", "Workspace not found"))
                    } catch (error: WorkspaceMembershipNotFoundException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.membership_not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "membership_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("membership_not_found", "Workspace membership not found"))
                    } catch (error: WorkspacePermissionDeniedException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.membership.update.forbidden",
                            status = HttpStatusCode.Forbidden.value,
                            safeErrorCode = error.reasonCode,
                        )
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse(error.reasonCode, "Workspace membership update is forbidden"))
                    }
                }

                post("/workspace-invitations/{membershipId}/respond") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "workspace_invitation_respond:${principal.user.id}", limit = 40, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val membershipId = call.parameters["membershipId"]
                    if (!membershipId.isUuid()) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.invalid_membership_id",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid invitation id"))
                        return@post
                    }
                    val resolvedMembershipId = membershipId.orEmpty()
                    val request = call.receiveJsonBodyLimited<RespondWorkspaceInvitationRequest>(
                        json = requestJson,
                        maxBytes = MAX_RESPOND_INVITATION_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "organizer.workspace.invitation.respond.payload_too_large",
                                status = HttpStatusCode.PayloadTooLarge.value,
                                safeErrorCode = "payload_too_large",
                            )
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                ErrorResponse("payload_too_large", "Request body is too large"),
                            )
                            return@post
                        }
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid workspace invitation response"))
                        return@post
                    }
                    val decision = WorkspaceInvitationDecision.fromWireName(request.decision)
                    if (decision == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.invalid_decision",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown workspace invitation decision"))
                        return@post
                    }
                    try {
                        workspaceService.respondToInvitation(
                            userId = principal.user.id,
                            membershipId = resolvedMembershipId,
                            accept = decision == WorkspaceInvitationDecision.ACCEPT,
                        )
                        logger.info(
                            "organizer.workspace.invitation.respond.success requestId={} userId={} membershipId={} decision={}",
                            requestId,
                            principal.user.id,
                            membershipId,
                            decision.wireName,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.success",
                            status = HttpStatusCode.OK.value,
                            metadata = mapOf("decision" to decision.wireName),
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            WorkspaceInvitationDecisionResponse(status = decision.wireName),
                        )
                    } catch (error: WorkspaceInvitationNotFoundException) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "organizer.workspace.invitation.respond.not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "invitation_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("invitation_not_found", "Workspace invitation not found"))
                    }
                }
            }
        }
    }

    /** Генерирует fallback slug из имени workspace, если пользователь не передал свой. */
    private fun generatedSlug(name: String): String {
        val base = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { "workspace" }
        val suffix = UUID.randomUUID().toString().take(6)
        return "$base-$suffix"
    }
}

/** DTO запроса на создание organizer workspace. */
@Serializable
data class CreateWorkspaceRequest(
    val name: String,
    val slug: String? = null,
)

/** DTO ответа списка organizer workspaces. */
@Serializable
data class WorkspaceListResponse(
    val workspaces: List<WorkspaceResponse>,
)

/** DTO одного organizer workspace с roster и permission capabilities текущего viewer. */
@Serializable
data class WorkspaceResponse(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    @SerialName("permission_role")
    val permissionRole: String,
    @SerialName("can_manage_members")
    val canManageMembers: Boolean,
    @SerialName("assignable_permission_roles")
    val assignablePermissionRoles: List<String>,
    val memberships: List<WorkspaceMembershipResponse>,
)

/** DTO membership внутри organizer workspace. */
@Serializable
data class WorkspaceMembershipResponse(
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
    val isCurrentUser: Boolean,
    @SerialName("can_edit_role")
    val canEditRole: Boolean,
    @SerialName("assignable_permission_roles")
    val assignablePermissionRoles: List<String>,
)

/** DTO inbox списка pending invitations текущего пользователя. */
@Serializable
data class WorkspaceInvitationListResponse(
    val invitations: List<WorkspaceInvitationResponse>,
)

/** DTO pending invitation текущего пользователя. */
@Serializable
data class WorkspaceInvitationResponse(
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
)

/** DTO запроса на создание pending invitation. */
@Serializable
data class CreateWorkspaceInvitationRequest(
    @SerialName("invitee_identifier")
    val inviteeIdentifier: String,
    @SerialName("permission_role")
    val permissionRole: String,
)

/** DTO запроса на update membership permission role. */
@Serializable
data class UpdateWorkspaceMembershipRoleRequest(
    @SerialName("permission_role")
    val permissionRole: String,
)

/** DTO запроса на accept/decline invitation. */
@Serializable
data class RespondWorkspaceInvitationRequest(
    val decision: String,
)

/** DTO ответа после принятия или отклонения invitation. */
@Serializable
data class WorkspaceInvitationDecisionResponse(
    val status: String,
)

/** Wire enum решений invitee по pending invitation. */
private enum class WorkspaceInvitationDecision(
    val wireName: String,
) {
    ACCEPT("accept"),
    DECLINE("decline"),
    ;

    companion object {
        fun fromWireName(value: String): WorkspaceInvitationDecision? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/** Преобразует stored workspace в HTTP DTO с viewer-specific capabilities. */
private fun StoredWorkspace.toResponse(
    currentUserId: String,
    workspaceService: OrganizerWorkspaceService,
): WorkspaceResponse {
    val assignableRoles = workspaceService.assignableRoles(permissionRole).map(WorkspacePermissionRole::wireName)
    return WorkspaceResponse(
        id = id,
        name = name,
        slug = slug,
        status = status,
        permissionRole = permissionRole.wireName,
        canManageMembers = workspaceService.canManageMembers(permissionRole),
        assignablePermissionRoles = assignableRoles,
        memberships = memberships.map {
            it.toResponse(
                currentUserId = currentUserId,
                workspaceService = workspaceService,
                viewerPermissionRole = permissionRole,
            )
        },
    )
}

/** Преобразует membership в HTTP DTO с edit flags для текущего viewer. */
private fun StoredWorkspaceMembership.toResponse(
    currentUserId: String,
    workspaceService: OrganizerWorkspaceService,
    viewerPermissionRole: WorkspacePermissionRole,
): WorkspaceMembershipResponse {
    val assignableRoles = workspaceService.membershipAssignableRoles(
        actorPermissionRole = viewerPermissionRole,
        actorUserId = currentUserId,
        membership = this,
    ).map(WorkspacePermissionRole::wireName)
    return WorkspaceMembershipResponse(
        membershipId = membershipId,
        userId = userId,
        displayName = displayName,
        username = username,
        permissionRole = permissionRole.wireName,
        status = status.wireName,
        invitedByDisplayName = invitedByDisplayName,
        isCurrentUser = userId == currentUserId,
        canEditRole = assignableRoles.isNotEmpty(),
        assignablePermissionRoles = assignableRoles,
    )
}

/** Преобразует pending invitation inbox item в HTTP DTO. */
private fun StoredWorkspaceInvitation.toResponse(): WorkspaceInvitationResponse {
    return WorkspaceInvitationResponse(
        membershipId = membershipId,
        workspaceId = workspaceId,
        workspaceName = workspaceName,
        workspaceSlug = workspaceSlug,
        workspaceStatus = workspaceStatus,
        permissionRole = permissionRole.wireName,
        invitedByDisplayName = invitedByDisplayName,
    )
}

/** Проверяет, что path value содержит валидный UUID. */
private fun String?.isUuid(): Boolean {
    if (this == null) return false
    return runCatching { UUID.fromString(this) }.isSuccess
}
