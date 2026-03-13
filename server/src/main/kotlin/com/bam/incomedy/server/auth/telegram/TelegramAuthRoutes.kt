package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.http.PayloadTooLargeException
import com.bam.incomedy.server.http.receiveJsonBodyLimited
import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
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

/** Public Telegram verify route с безопасной диагностикой проблемных auth попыток. */
object TelegramAuthRoutes {
    /** Структурированный logger текущих Telegram auth routes. */
    private val logger = LoggerFactory.getLogger(TelegramAuthRoutes::class.java)

    /** Строгий JSON parser для verify payload без silently ignored полей. */
    private val requestJson = Json {
        ignoreUnknownKeys = false
    }

    /** Максимально допустимый размер Telegram verify body. */
    private const val MAX_VERIFY_REQUEST_BYTES = 4 * 1024

    /** Rate limit по direct peer для Telegram verify. */
    private const val VERIFY_DIRECT_PEER_LIMIT = 600

    /** Rate limit по Telegram account id для Telegram verify. */
    private const val VERIFY_TELEGRAM_ID_LIMIT = 20

    /** Регистрирует Telegram auth verification endpoint. */
    fun register(
        route: Route,
        authService: TelegramAuthService,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
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
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.telegram.verify.rate_limited",
                    status = HttpStatusCode.TooManyRequests.value,
                    safeErrorCode = "rate_limited",
                    metadata = mapOf("scope" to "peer"),
                )
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
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.telegram.verify.payload_too_large",
                        status = HttpStatusCode.PayloadTooLarge.value,
                        safeErrorCode = "payload_too_large",
                    )
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorResponse("payload_too_large", "Request body is too large"),
                    )
                    return@post
                }
                logger.warn("auth.telegram.verify.invalid_payload requestId={} peer={}", requestId, directPeer)
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.telegram.verify.invalid_payload",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
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
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.telegram.verify.rate_limited",
                    status = HttpStatusCode.TooManyRequests.value,
                    safeErrorCode = "rate_limited",
                    metadata = mapOf("scope" to "telegram_id"),
                )
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
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.telegram.verify.success",
                        status = HttpStatusCode.OK.value,
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
                    val safeErrorCode = safeFailureCode(error = error, status = status)
                    logger.warn(
                        "auth.telegram.verify.failed requestId={} status={} reason={}",
                        requestId,
                        status.value,
                        error.message ?: "unknown",
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.telegram.verify.failed",
                        status = status.value,
                        safeErrorCode = safeErrorCode,
                    )
                    call.respond(
                        status,
                        ErrorResponse("telegram_auth_failed", safeMessage),
                    )
                },
            )
        }
    }

    /** Преобразует внутреннюю причину verify failure в безопасный машинный код. */
    private fun safeFailureCode(error: Throwable, status: HttpStatusCode): String {
        if (error is ReplayedTelegramAuthException) {
            return "telegram_auth_replayed"
        }
        val message = error.message?.lowercase().orEmpty()
        return when {
            "hash" in message -> "telegram_auth_hash_invalid"
            "expired" in message -> "telegram_auth_expired"
            "auth_date" in message -> "telegram_auth_date_invalid"
            "username" in message -> "telegram_username_invalid"
            "photo_url" in message -> "telegram_photo_url_invalid"
            status == HttpStatusCode.BadRequest -> "telegram_auth_bad_request"
            else -> "telegram_auth_failed"
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
