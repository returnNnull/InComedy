package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.db.TelegramUserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
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
    ) {
        route.get("/api/v1/auth/session/me") {
            val requestId = call.callId ?: "n/a"
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
                    ErrorResponse("unauthorized", error.message ?: "Invalid access token"),
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
                    ErrorResponse("unauthorized", error.message ?: "Invalid access token"),
                )
                return@post
            }

            userRepository.revokeSessions(verified.userId, Instant.now())
            logger.info("auth.logout.success requestId={} userId={}", requestId, verified.userId)
            call.respond(HttpStatusCode.NoContent)
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
