package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

object TelegramAuthRoutes {
    private val logger = LoggerFactory.getLogger(TelegramAuthRoutes::class.java)
    private val requestJson = Json {
        ignoreUnknownKeys = false
    }

    private const val MAX_VERIFY_REQUEST_BYTES = 4 * 1024
    private const val VERIFY_DIRECT_PEER_LIMIT = 600
    private const val VERIFY_TELEGRAM_ID_LIMIT = 20

    fun register(route: Route, authService: TelegramAuthService, rateLimiter: AuthRateLimiter) {
        route.post("/api/v1/auth/telegram/verify") {
            val requestId = call.callId ?: "n/a"
            val directPeer = call.directPeerFingerprint()
            if (!rateLimiter.allow(
                    key = "telegram_verify_peer:$directPeer",
                    limit = VERIFY_DIRECT_PEER_LIMIT,
                    windowMs = 60_000L,
                )
            ) {
                logger.warn("auth.telegram.verify.rate_limited requestId={} peer={}", requestId, directPeer)
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@post
            }
            val request = call.receiveJsonBodyLimited<TelegramVerifyRequest>(
                json = requestJson,
                maxBytes = MAX_VERIFY_REQUEST_BYTES,
            ).getOrElse { error ->
                if (error is PayloadTooLargeException) {
                    logger.warn("auth.telegram.verify.payload_too_large requestId={} peer={}", requestId, directPeer)
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorResponse("payload_too_large", "Request body is too large"),
                    )
                    return@post
                }
                logger.warn("auth.telegram.verify.invalid_payload requestId={} peer={}", requestId, directPeer)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Invalid Telegram auth payload"),
                )
                return@post
            }
            if (!rateLimiter.allow(
                    key = "telegram_verify_id:${request.id}",
                    limit = VERIFY_TELEGRAM_ID_LIMIT,
                    windowMs = 60_000L,
                )
            ) {
                logger.warn("auth.telegram.verify.rate_limited requestId={} telegramId={}", requestId, request.id)
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@post
            }
            logger.info(
                "auth.telegram.verify.received requestId={} telegramId={} hasUsername={} hasPhoto={}",
                requestId,
                request.id,
                !request.username.isNullOrBlank(),
                !request.photoUrl.isNullOrBlank(),
            )

            val result = authService.verifyAndCreateSession(request)
            result.fold(
                onSuccess = { auth ->
                    logger.info(
                        "auth.telegram.verify.success requestId={} userId={}",
                        requestId,
                        auth.user.id,
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        TelegramAuthResponse(
                            accessToken = auth.accessToken,
                            refreshToken = auth.refreshToken,
                            expiresInSeconds = auth.expiresInSeconds,
                            user = TelegramAuthUserResponse(
                                id = auth.user.id,
                                displayName = auth.user.displayName,
                                username = auth.user.username,
                                photoUrl = auth.user.photoUrl,
                            ),
                        ),
                    )
                },
                onFailure = { error ->
                    val status = if (error.message?.contains("hash", ignoreCase = true) == true ||
                        error.message?.contains("expired", ignoreCase = true) == true ||
                        error is ReplayedTelegramAuthException
                    ) {
                        HttpStatusCode.Unauthorized
                    } else {
                        HttpStatusCode.BadRequest
                    }
                    val safeMessage = if (status == HttpStatusCode.Unauthorized) {
                        "Telegram auth failed"
                    } else {
                        "Invalid auth request"
                    }
                    logger.warn(
                        "auth.telegram.verify.failed requestId={} status={} reason={}",
                        requestId,
                        status.value,
                        error.message ?: "unknown",
                    )
                    call.respond(
                        status,
                        ErrorResponse("telegram_auth_failed", safeMessage),
                    )
                },
            )
        }
    }
}

@Serializable
data class TelegramAuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresInSeconds: Long,
    val user: TelegramAuthUserResponse,
)

@Serializable
data class TelegramAuthUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
)
