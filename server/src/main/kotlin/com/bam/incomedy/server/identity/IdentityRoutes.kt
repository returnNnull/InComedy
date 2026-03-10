package com.bam.incomedy.server.identity

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.requireSessionPrincipal
import com.bam.incomedy.server.auth.session.withSessionAuth
import com.bam.incomedy.server.db.UserRepository
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
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

object IdentityRoutes {
    private val logger = LoggerFactory.getLogger(IdentityRoutes::class.java)
    private val requestJson = Json { ignoreUnknownKeys = false }
    private const val MAX_ACTIVE_ROLE_REQUEST_BYTES = 1024

    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        userRepository: UserRepository,
        rateLimiter: AuthRateLimiter,
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
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@get
                    }
                    val user = userRepository.findById(principal.user.id)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "User not found"))
                        return@get
                    }
                    logger.info("identity.me.roles.success requestId={} userId={}", requestId, user.id)
                    call.respond(HttpStatusCode.OK, user.toRolesResponse())
                }

                post("/me/active-role") {
                    val requestId = call.callId ?: "n/a"
                    val principal = call.requireSessionPrincipal()
                    if (!rateLimiter.allow(key = "active_role:${principal.user.id}", limit = 60, windowMs = 60_000L)) {
                        call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                        return@post
                    }
                    val request = call.receiveJsonBodyLimited<SetActiveRoleRequest>(
                        json = requestJson,
                        maxBytes = MAX_ACTIVE_ROLE_REQUEST_BYTES,
                    ).getOrElse { error ->
                        if (error is PayloadTooLargeException) {
                            call.respond(
                                HttpStatusCode.PayloadTooLarge,
                                ErrorResponse("payload_too_large", "Request body is too large"),
                            )
                            return@post
                        }
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid active role request"))
                        return@post
                    }
                    val role = UserRole.fromWireName(request.role)
                    if (role == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Unknown role"))
                        return@post
                    }
                    val updatedUser = userRepository.setActiveRole(principal.user.id, role)
                    if (updatedUser == null) {
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
