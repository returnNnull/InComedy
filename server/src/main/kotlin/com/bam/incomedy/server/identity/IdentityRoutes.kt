package com.bam.incomedy.server.identity

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.UserRole
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

/** Identity routes для чтения ролей и переключения активной роли с diagnostics capture. */
object IdentityRoutes {
    /** Структурированный logger identity routes. */
    private val logger = LoggerFactory.getLogger(IdentityRoutes::class.java)

    /** Строгий JSON parser для active-role payload. */
    private val requestJson = Json { ignoreUnknownKeys = false }

    /** Максимально допустимый размер тела запроса active-role. */
    private const val MAX_ACTIVE_ROLE_REQUEST_BYTES = 1024

    /** Регистрирует identity routes для role context management. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        userRepository: SessionUserRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.route("/api/v1") {
            withSessionAuth(
                tokenService = tokenService,
                userRepository = userRepository,
                rateLimiter = rateLimiter,
            ) {
                get("/me/roles") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "me_roles:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "identity.me.roles.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }
                    val user = userRepository.findById(principal.user.id)
                    if (user == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "identity.me.roles.not_found",
                            status = HttpStatusCode.NotFound.value,
                            safeErrorCode = "not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "User not found"))
                        return@get
                    }
                    logger.info("identity.me.roles.success requestId={} userId={}", requestId, user.id)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "identity.me.roles.success",
                        status = HttpStatusCode.OK.value,
                    )
                    call.respond(HttpStatusCode.OK, user.toRolesResponse())
                }

                post("/me/active-role") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "active_role:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "identity.me.active_role.rate_limited",
                            status = HttpStatusCode.TooManyRequests.value,
                            safeErrorCode = "rate_limited",
                        )
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<SetActiveRoleRequest>(
                        json = requestJson,
                        maxBytes = MAX_ACTIVE_ROLE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "identity.me.active_role.payload_too_large",
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
                            stage = "identity.me.active_role.invalid_payload",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid active role request"))
                        return@post
                    }
                    val role = UserRole.fromWireName(request.role)
                    if (role == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "identity.me.active_role.unknown_role",
                            status = HttpStatusCode.BadRequest.value,
                            safeErrorCode = "bad_request",
                        )
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown role"))
                        return@post
                    }
                    val updatedUser = userRepository.setActiveRole(principal.user.id, role)
                    if (updatedUser == null) {
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "identity.me.active_role.forbidden",
                            status = HttpStatusCode.Forbidden.value,
                            safeErrorCode = "forbidden",
                        )
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse("forbidden", "Role is not assigned to the user"),
                        )
                        return@post
                    }
                    logger.info(
                        "identity.me.active_role.updated requestId={} userId={} role={}",
                        requestId,
                        updatedUser.id,
                        role.wireName,
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "identity.me.active_role.success",
                        status = HttpStatusCode.OK.value,
                    )
                    call.respond(HttpStatusCode.OK, updatedUser.toRolesResponse())
                }
            }
        }
    }
}

@Serializable
data class MeRolesResponse(
    val roles: List<String>,
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String>,
)

@Serializable
data class SetActiveRoleRequest(
    val role: String,
)

private fun com.bam.incomedy.server.db.StoredUser.toRolesResponse(): MeRolesResponse {
    return MeRolesResponse(
        roles = roles.map { it.wireName }.sorted(),
        activeRole = activeRole?.wireName,
        linkedProviders = linkedProviders.map { it.wireName }.sorted(),
    )
}
