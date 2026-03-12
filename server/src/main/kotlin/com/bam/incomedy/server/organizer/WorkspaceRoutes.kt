package com.bam.incomedy.server.organizer

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.StoredWorkspace
import com.bam.incomedy.server.db.UserRepository
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
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/** Organizer workspace routes с диагностикой list/create сценариев. */
object WorkspaceRoutes {
    /** Структурированный logger organizer workspace routes. */
    private val logger = LoggerFactory.getLogger(WorkspaceRoutes::class.java)

    /** Строгий JSON parser для workspace create payload. */
    private val requestJson = Json { ignoreUnknownKeys = false }

    /** Максимально допустимый размер тела запроса на создание workspace. */
    private const val MAX_CREATE_WORKSPACE_REQUEST_BYTES = 2 * 1024

    /** Валидация slug рабочего пространства. */
    private val slugRegex = Regex("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$")

    /** Регистрирует organizer workspace list/create routes. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        userRepository: UserRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = userRepository,
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
                    val workspaces = userRepository.listWorkspaces(principal.user.id).map(StoredWorkspace::toResponse)
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
                    val workspace = userRepository.createWorkspace(
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
                    call.respond(HttpStatusCode.Created, workspace.toResponse())
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

@Serializable
data class CreateWorkspaceRequest(
    val name: String,
    val slug: String? = null,
)

@Serializable
data class WorkspaceListResponse(
    val workspaces: List<WorkspaceResponse>,
)

@Serializable
data class WorkspaceResponse(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    @SerialName("permission_role")
    val permissionRole: String,
)

private fun StoredWorkspace.toResponse(): WorkspaceResponse {
    return WorkspaceResponse(
        id = id,
        name = name,
        slug = slug,
        status = status,
        permissionRole = permissionRole.wireName,
    )
}
