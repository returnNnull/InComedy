package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.toResponse
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

object VkIdAuthRoutes {
    private val logger = LoggerFactory.getLogger(VkIdAuthRoutes::class.java)
    private val requestJson = Json { ignoreUnknownKeys = false }

    private const val MAX_VERIFY_REQUEST_BYTES = 4 * 1024
    private const val START_DIRECT_PEER_LIMIT = 120
    private const val VERIFY_DIRECT_PEER_LIMIT = 240

    fun register(
        route: Route,
        authService: VkIdAuthService?,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.get("/api/v1/auth/vk/start") {
            val requestId = call.callId ?: "n/a"
            val directPeer = call.directPeerFingerprint()
            if (!rateLimiter.allow(key = "vk_start_peer:$directPeer", limit = START_DIRECT_PEER_LIMIT, windowMs = 60_000L)) {
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.vk.start.rate_limited",
                    status = HttpStatusCode.TooManyRequests.value,
                    safeErrorCode = "rate_limited",
                )
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                return@get
            }
            if (authService == null) {
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.vk.start.disabled",
                    status = HttpStatusCode.ServiceUnavailable.value,
                    safeErrorCode = "vk_auth_unavailable",
                )
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("vk_auth_unavailable", "VK auth is not configured"))
                return@get
            }
            val launch = authService.createLaunchRequest()
            logger.info("auth.vk.start.success requestId={}", requestId)
            diagnosticsStore?.recordCall(
                call = call,
                stage = "auth.vk.start.success",
                status = HttpStatusCode.OK.value,
            )
            call.respond(
                HttpStatusCode.OK,
                VkIdStartResponse(
                    authUrl = launch.authUrl,
                    state = launch.state,
                ),
            )
        }

        route.post("/api/v1/auth/vk/verify") {
            val requestId = call.callId ?: "n/a"
            val directPeer = call.directPeerFingerprint()
            if (!rateLimiter.allow(key = "vk_verify_peer:$directPeer", limit = VERIFY_DIRECT_PEER_LIMIT, windowMs = 60_000L)) {
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.vk.verify.rate_limited",
                    status = HttpStatusCode.TooManyRequests.value,
                    safeErrorCode = "rate_limited",
                )
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
                return@post
            }
            if (authService == null) {
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.vk.verify.disabled",
                    status = HttpStatusCode.ServiceUnavailable.value,
                    safeErrorCode = "vk_auth_unavailable",
                )
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("vk_auth_unavailable", "VK auth is not configured"))
                return@post
            }
            val request = call.receiveJsonBodyLimited<VkIdVerifyPayload>(
                json = requestJson,
                maxBytes = MAX_VERIFY_REQUEST_BYTES,
            ).getOrElse { error ->
                if (error is PayloadTooLargeException) {
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.vk.verify.payload_too_large",
                        status = HttpStatusCode.PayloadTooLarge.value,
                        safeErrorCode = "payload_too_large",
                    )
                    call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("payload_too_large", "Request body is too large"))
                    return@post
                }
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.vk.verify.invalid_payload",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "bad_request",
                )
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid VK auth payload"))
                return@post
            }

            runCatching {
                authService.verifyAndCreateSession(
                    VkIdVerifyRequest(
                        code = request.code,
                        state = request.state,
                        deviceId = request.deviceId,
                    ),
                )
            }.fold(
                onSuccess = { auth ->
                    logger.info("auth.vk.verify.success requestId={} userId={}", requestId, auth.user.id)
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.vk.verify.success",
                        status = HttpStatusCode.OK.value,
                    )
                    call.respond(HttpStatusCode.OK, auth.toResponse())
                },
                onFailure = { error ->
                    val status = when (error) {
                        is InvalidVkIdAuthStateException -> HttpStatusCode.BadRequest
                        is VkIdCodeExchangeException,
                        is VkIdUserInfoException -> HttpStatusCode.Unauthorized
                        else -> HttpStatusCode.BadRequest
                    }
                    val code = when (error) {
                        is InvalidVkIdAuthStateException -> "vk_auth_state_invalid"
                        is VkIdCodeExchangeException -> "vk_auth_code_exchange_failed"
                        is VkIdUserInfoException -> "vk_auth_user_info_failed"
                        else -> "vk_auth_failed"
                    }
                    logger.warn(
                        "auth.vk.verify.failed requestId={} status={} reason={}",
                        requestId,
                        status.value,
                        error.message ?: "unknown",
                    )
                    diagnosticsStore?.recordCall(
                        call = call,
                        stage = "auth.vk.verify.failed",
                        status = status.value,
                        safeErrorCode = code,
                    )
                    call.respond(status, ErrorResponse(code, "VK auth failed"))
                },
            )
        }
    }
}

@Serializable
data class VkIdStartResponse(
    @SerialName("auth_url")
    val authUrl: String,
    val state: String,
)

@Serializable
data class VkIdVerifyPayload(
    val code: String,
    val state: String,
    @SerialName("device_id")
    val deviceId: String,
)
