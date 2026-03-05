package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.db.TelegramUserRepository
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.clientFingerprint
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Instant

object SessionRoutes {
    private val logger = LoggerFactory.getLogger(SessionRoutes::class.java)

    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        userRepository: TelegramUserRepository,
        rateLimiter: AuthRateLimiter,
    ) {
        route.get("/api/v1/auth/session/me") {
            val requestId = call.callId ?: "n/a"
            val client = call.clientFingerprint()
            if (!rateLimiter.allow(key = "session_me:$client", limit = 120, windowMs = 60_000L)) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@get
            }
            val bearerToken = extractBearerToken(call.request.headers["Authorization"])
            if (bearerToken.isBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing bearer token"),
                )
                return@get
            }

            val verified = tokenService.verifyAccessToken(bearerToken).getOrElse { error ->
                logger.warn(
                    "auth.session.me.invalid_token requestId={} reason={}",
                    requestId,
                    error.message ?: "unknown",
                )
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid access token"),
                )
                return@get
            }

            val user = userRepository.findById(verified.userId)
            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "User not found for token"),
                )
                return@get
            }
            if (user.sessionRevokedAt != null && !verified.issuedAt.isAfter(user.sessionRevokedAt)) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Session revoked"),
                )
                return@get
            }

            logger.info(
                "auth.session.me.success requestId={} userId={}",
                requestId,
                user.id,
            )
            call.respond(
                HttpStatusCode.OK,
                SessionMeResponse(
                    user = SessionUserResponse(
                        id = user.id,
                        telegramId = user.telegramId,
                        displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim(),
                        username = user.username,
                        photoUrl = user.photoUrl,
                    ),
                ),
            )
        }

        route.post("/api/v1/auth/logout") {
            val requestId = call.callId ?: "n/a"
            val client = call.clientFingerprint()
            if (!rateLimiter.allow(key = "logout:$client", limit = 30, windowMs = 60_000L)) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@post
            }
            val bearerToken = extractBearerToken(call.request.headers["Authorization"])
            if (bearerToken.isBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing bearer token"),
                )
                return@post
            }

            val verified = tokenService.verifyAccessToken(bearerToken).getOrElse { error ->
                logger.warn(
                    "auth.logout.invalid_token requestId={} reason={}",
                    requestId,
                    error.message ?: "unknown",
                )
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid access token"),
                )
                return@post
            }

            userRepository.revokeSessions(verified.userId, Instant.now())
            logger.info("auth.logout.success requestId={} userId={}", requestId, verified.userId)
            call.respond(HttpStatusCode.NoContent)
        }

        route.post("/api/v1/auth/refresh") {
            val requestId = call.callId ?: "n/a"
            val client = call.clientFingerprint()
            if (!rateLimiter.allow(key = "refresh:$client", limit = 30, windowMs = 60_000L)) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@post
            }
            val request = runCatching { call.receive<RefreshRequest>() }.getOrElse {
                logger.warn("auth.refresh.invalid_payload requestId={}", requestId)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Invalid refresh request payload"),
                )
                return@post
            }
            if (request.refreshToken.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Refresh token is required"),
                )
                return@post
            }

            val now = Instant.now()
            val refreshHash = tokenService.refreshTokenHash(request.refreshToken)
            val user = userRepository.consumeRefreshToken(refreshHash, now)
            if (user == null) {
                logger.warn("auth.refresh.invalid_token requestId={}", requestId)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid refresh token"),
                )
                return@post
            }
            if (user.sessionRevokedAt != null) {
                logger.warn("auth.refresh.revoked requestId={} userId={}", requestId, user.id)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Session revoked"),
                )
                return@post
            }

            val newTokens = tokenService.issue(
                userId = user.id,
                telegramUserId = user.telegramId,
            )
            userRepository.storeRefreshToken(
                userId = user.id,
                tokenHash = tokenService.refreshTokenHash(newTokens.refreshToken),
                expiresAt = tokenService.refreshExpiryInstant(now),
            )
            logger.info("auth.refresh.success requestId={} userId={}", requestId, user.id)
            call.respond(
                HttpStatusCode.OK,
                RefreshResponse(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken,
                    expiresIn = newTokens.expiresInSeconds,
                    user = SessionUserResponse(
                        id = user.id,
                        telegramId = user.telegramId,
                        displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim(),
                        username = user.username,
                        photoUrl = user.photoUrl,
                    ),
                ),
            )
        }
    }
}

private fun extractBearerToken(raw: String?): String {
    return raw?.removePrefix("Bearer ")?.trim().orEmpty()
}

@Serializable
data class SessionMeResponse(
    val user: SessionUserResponse,
)

@Serializable
data class SessionUserResponse(
    val id: String,
    @SerialName("telegram_id")
    val telegramId: Long,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    val user: SessionUserResponse,
)
