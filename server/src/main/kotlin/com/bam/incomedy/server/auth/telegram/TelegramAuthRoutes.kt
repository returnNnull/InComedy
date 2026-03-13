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
import io.ktor.server.routing.get
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

    /** Rate limit по direct peer для Telegram auth start. */
    private const val START_DIRECT_PEER_LIMIT = 120

    /** Rate limit по direct peer для Telegram verify. */
    private const val VERIFY_DIRECT_PEER_LIMIT = 600

    /** Регистрирует Telegram auth start/verify endpoints. */
    fun register(
        route: Route,
        authService: TelegramAuthService,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.get("/api/v1/auth/telegram/start") {
            val requestId = call.callId ?: "n/a"
            val directPeer = call.directPeerFingerprint()
            if (!rateLimiter.allow(
                    key = "telegram_start_peer:$directPeer",
                    limit = START_DIRECT_PEER_LIMIT,
                    windowMs = 60_000L,
                )
            ) {
                logger.warn("auth.telegram.start.rate_limited requestId={} peer={}", requestId, directPeer)
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.telegram.start.rate_limited",
                    status = HttpStatusCode.TooManyRequests.value,
                    safeErrorCode = "rate_limited",
                )
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("rate_limited", "Too many requests"),
                )
                return@get
            }

            val result = authService.createLaunchRequest()
            result.fold(
                onSuccess = { launch ->
                    logger.info("auth.telegram.start.success requestId={}", requestId)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.telegram.start.success",
                        status = HttpStatusCode.OK.value,
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        TelegramAuthStartResponse(
                            authUrl = launch.authUrl,
                            state = launch.state,
                        ),
                    )
                },
                onFailure = { error ->
                    logger.error(
                        "auth.telegram.start.failed requestId={} reason={}",
                        requestId,
                        error.message ?: "unknown",
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.telegram.start.failed",
                        status = HttpStatusCode.InternalServerError.value,
                        safeErrorCode = "telegram_auth_start_failed",
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse("telegram_auth_start_failed", "Unable to start Telegram auth"),
                    )
                },
            )
        }

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
            logger.info(
                "auth.telegram.verify.received requestId={} hasCode={} statePresent={}",
                requestId,
                request.code.isNotBlank(),
                request.state.isNotBlank(),
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
                    val status = failureStatus(error)
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
        return when (error) {
            is ReplayedTelegramAuthException -> "telegram_auth_replayed"
            is InvalidTelegramAuthStateException -> "telegram_auth_state_invalid"
            is TelegramOidcExchangeException -> "telegram_auth_code_exchange_failed"
            is TelegramIdTokenVerificationException -> "telegram_auth_id_token_invalid"
            else -> if (status == HttpStatusCode.BadRequest) {
                "telegram_auth_bad_request"
            } else {
                "telegram_auth_failed"
            }
        }
    }

    /** Определяет HTTP-статус для конкретной Telegram auth failure причины. */
    private fun failureStatus(error: Throwable): HttpStatusCode {
        return when (error) {
            is ReplayedTelegramAuthException,
            is TelegramOidcExchangeException,
            is TelegramIdTokenVerificationException -> HttpStatusCode.Unauthorized
            is InvalidTelegramAuthStateException -> HttpStatusCode.BadRequest
            else -> HttpStatusCode.BadRequest
        }
    }
}

/**
 * Успешный backend-ответ после завершения Telegram auth.
 *
 * @property accessToken Внутренний access token.
 * @property refreshToken Внутренний refresh token.
 * @property expiresInSeconds TTL access token в секундах.
 * @property user Профиль авторизованного пользователя.
 */
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

/**
 * Профиль пользователя в успешном Telegram auth response.
 *
 * @property id Внутренний user id.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля, если он привязан.
 * @property photoUrl Ссылка на аватар профиля.
 */
@Serializable
data class TelegramAuthUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
)
