package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.TelegramUserRepository
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

data class SessionPrincipal(
    val verifiedToken: VerifiedAccessToken,
    val user: StoredUser,
)

private val logger = LoggerFactory.getLogger("SessionAuthPlugin")
private val sessionPrincipalKey = AttributeKey<SessionPrincipal>("session_principal")
private object SessionAuthRouteSelector : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(session-auth)"
}

fun Route.withSessionAuth(
    tokenService: JwtSessionTokenService,
    userRepository: TelegramUserRepository,
    rateLimiter: AuthRateLimiter,
    build: Route.() -> Unit,
) {
    val authenticatedRoute = createChild(SessionAuthRouteSelector)
    authenticatedRoute.intercept(ApplicationCallPipeline.Plugins) {
        val requestId = call.callId ?: "n/a"
        val directPeer = call.directPeerFingerprint()
        if (!rateLimiter.allow(key = "protected_peer:$directPeer", limit = 600, windowMs = 60_000L)) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse("rate_limited", "Too many requests"),
            )
            finish()
            return@intercept
        }
        val bearerToken = call.request.headers["Authorization"]
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        if (bearerToken.isBlank()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", "Missing bearer token"),
            )
            finish()
            return@intercept
        }

        val verified = tokenService.verifyAccessToken(bearerToken).getOrElse { error ->
            logger.warn(
                "auth.session.protected.invalid_token requestId={} reason={}",
                requestId,
                error.message ?: "unknown",
            )
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", "Invalid access token"),
            )
            finish()
            return@intercept
        }

        val user = userRepository.findById(verified.userId)
        if (user == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", "User not found for token"),
            )
            finish()
            return@intercept
        }
        if (user.sessionRevokedAt != null && !verified.issuedAt.isAfter(user.sessionRevokedAt)) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("unauthorized", "Session revoked"),
            )
            finish()
            return@intercept
        }

        call.attributes.put(
            sessionPrincipalKey,
            SessionPrincipal(
                verifiedToken = verified,
                user = user,
            ),
        )
    }
    authenticatedRoute.build()
}

fun ApplicationCall.requireSessionPrincipal(): SessionPrincipal {
    return attributes[sessionPrincipalKey]
}
