package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.StoredUser
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
import io.ktor.server.routing.route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant

/** Session lifecycle routes с безопасной диагностикой restore/refresh/logout проблем. */
object SessionRoutes {
    /** Структурированный logger session routes. */
    private val logger = LoggerFactory.getLogger(SessionRoutes::class.java)

    /** Строгий JSON parser для refresh request payload. */
    private val requestJson = Json {
        ignoreUnknownKeys = false
    }

    /** Валидация формата opaque refresh token. */
    private val refreshTokenRegex = Regex("^[A-Za-z0-9_-]{43}$")

    /** Максимально допустимый размер refresh body. */
    private const val MAX_REFRESH_REQUEST_BYTES = 2 * 1024

    /** Регистрирует session validation, refresh и logout endpoints. */
    fun register(
        route: Route,
        tokenService: JwtSessionTokenService,
        userRepository: SessionUserRepository,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.route("/api/v1/auth") {
            route("") {
                withSessionAuth(
                    tokenService = tokenService,
                    userRepository = userRepository,
                    rateLimiter = rateLimiter,
                ) {
                    get("/session/me") {
                        val requestId = call.callId ?: "n/a"
                        val principal = call.requireSessionPrincipal()
                        if (!rateLimiter.allow(key = "session_me:${principal.user.id}", limit = 120, windowMs = 60_000L)) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "auth.session.me.rate_limited",
                                status = HttpStatusCode.TooManyRequests.value,
                                safeErrorCode = "rate_limited",
                            )
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                ErrorResponse("rate_limited", "Too many requests"),
                            )
                            return@get
                        }
                        val user = principal.user
                        logger.info(
                            "auth.session.me.success requestId={} userId={}",
                            requestId,
                            user.id,
                        )
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "auth.session.me.success",
                            status = HttpStatusCode.OK.value,
                        )
                        call.respond(
                            HttpStatusCode.OK,
                            SessionMeResponse(
                                provider = principal.verifiedToken.provider,
                                user = user.toSessionUserResponse(),
                            ),
                        )
                    }

                    post("/logout") {
                        val requestId = call.callId ?: "n/a"
                        val principal = call.requireSessionPrincipal()
                        if (!rateLimiter.allow(key = "logout:${principal.user.id}", limit = 30, windowMs = 60_000L)) {
                            diagnosticsStore?.recordCall(
                                call = call,
                                stage = "auth.logout.rate_limited",
                                status = HttpStatusCode.TooManyRequests.value,
                                safeErrorCode = "rate_limited",
                            )
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                ErrorResponse("rate_limited", "Too many requests"),
                            )
                            return@post
                        }
                        userRepository.revokeSessions(principal.user.id, Instant.now())
                        logger.info("auth.logout.success requestId={} userId={}", requestId, principal.user.id)
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "auth.logout.success",
                            status = HttpStatusCode.NoContent.value,
                        )
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

            post("/refresh") {
                val requestId = call.callId ?: "n/a"
                val directPeer = call.directPeerFingerprint()
                if (!rateLimiter.allow(key = "refresh_peer:$directPeer", limit = 600, windowMs = 60_000L)) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.rate_limited",
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
                val request = call.receiveJsonBodyLimited<RefreshRequest>(
                    json = requestJson,
                    maxBytes = MAX_REFRESH_REQUEST_BYTES,
                ).getOrElse { error ->
                    if (error is PayloadTooLargeException) {
                        logger.warn("auth.refresh.payload_too_large requestId={} peer={}", requestId, directPeer)
                        diagnosticsStore?.recordCall(
                            call = call,
                            stage = "auth.refresh.payload_too_large",
                            status = HttpStatusCode.PayloadTooLarge.value,
                            safeErrorCode = "payload_too_large",
                        )
                        call.respond(
                            HttpStatusCode.PayloadTooLarge,
                            ErrorResponse("payload_too_large", "Request body is too large"),
                        )
                        return@post
                    }
                    logger.warn("auth.refresh.invalid_payload requestId={} peer={}", requestId, directPeer)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.invalid_payload",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "Invalid refresh request payload"),
                    )
                    return@post
                }
                if (!refreshTokenRegex.matches(request.refreshToken)) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.invalid_token_format",
                        status = HttpStatusCode.BadRequest.value,
                        safeErrorCode = "bad_request",
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("bad_request", "Invalid refresh token format"),
                    )
                    return@post
                }

                val now = Instant.now()
                val refreshHash = tokenService.refreshTokenHash(request.refreshToken)
                if (!rateLimiter.allow(key = "refresh_token:$refreshHash", limit = 30, windowMs = 60_000L)) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.rate_limited",
                        status = HttpStatusCode.TooManyRequests.value,
                        safeErrorCode = "rate_limited",
                        metadata = mapOf("scope" to "token"),
                    )
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("rate_limited", "Too many requests"),
                    )
                    return@post
                }
                val user = userRepository.consumeRefreshToken(refreshHash, now)
                if (user == null) {
                    logger.warn("auth.refresh.invalid_token requestId={}", requestId)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.invalid_token",
                        status = HttpStatusCode.Unauthorized.value,
                        safeErrorCode = "invalid_refresh_token",
                    )
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("unauthorized", "Invalid refresh token"),
                    )
                    return@post
                }
                if (user.sessionRevokedAt != null) {
                    logger.warn("auth.refresh.revoked requestId={} userId={}", requestId, user.id)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.refresh.revoked",
                        status = HttpStatusCode.Unauthorized.value,
                        safeErrorCode = "session_revoked",
                    )
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("unauthorized", "Session revoked"),
                    )
                    return@post
                }

                val newTokens = tokenService.issue(
                    userId = user.id,
                    provider = principalProvider(user),
                )
                userRepository.storeRefreshToken(
                    userId = user.id,
                    tokenHash = tokenService.refreshTokenHash(newTokens.refreshToken),
                    expiresAt = tokenService.refreshExpiryInstant(now),
                )
                logger.info("auth.refresh.success requestId={} userId={}", requestId, user.id)
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.refresh.success",
                    status = HttpStatusCode.OK.value,
                )
                call.respond(
                    HttpStatusCode.OK,
                    RefreshResponse(
                        provider = principalProvider(user).wireName,
                        accessToken = newTokens.accessToken,
                        refreshToken = newTokens.refreshToken,
                        expiresIn = newTokens.expiresInSeconds,
                        user = user.toSessionUserResponse(),
                    ),
                )
            }
        }
    }
}

@Serializable
data class SessionMeResponse(
    val provider: String,
    val user: SessionUserResponse,
)

@Serializable
data class SessionUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val provider: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    val user: SessionUserResponse,
)

private fun StoredUser.toSessionUserResponse(): SessionUserResponse {
    return SessionUserResponse(
        id = id,
        displayName = displayName,
        username = username,
        photoUrl = photoUrl,
        roles = roles.map { it.wireName }.sorted(),
        activeRole = activeRole?.wireName,
        linkedProviders = linkedProviders.map { it.wireName }.sorted(),
    )
}

private fun principalProvider(user: StoredUser) = user.linkedProviders.firstOrNull()
    ?: com.bam.incomedy.server.db.AuthProvider.PASSWORD
