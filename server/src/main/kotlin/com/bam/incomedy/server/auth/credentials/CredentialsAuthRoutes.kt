package com.bam.incomedy.server.auth.credentials

import com.bam.incomedy.server.ErrorResponse
import com.bam.incomedy.server.auth.toResponse
import com.bam.incomedy.server.db.DuplicateCredentialLoginException
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest

object CredentialsAuthRoutes {
    private val logger = LoggerFactory.getLogger(CredentialsAuthRoutes::class.java)
    private val requestJson = Json { ignoreUnknownKeys = false }

    private const val MAX_REQUEST_BYTES = 4 * 1024
    private const val REGISTER_DIRECT_PEER_LIMIT = 30
    private const val LOGIN_DIRECT_PEER_LIMIT = 120
    private const val LOGIN_LOGIN_LIMIT = 20
    private const val REGISTER_LOGIN_LIMIT = 10

    fun register(
        route: Route,
        authService: CredentialsAuthService,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore? = null,
    ) {
        route.post("/api/v1/auth/register") {
            call.handleCredentialRequest(
                routeName = "register",
                directPeerLimit = REGISTER_DIRECT_PEER_LIMIT,
                loginLimit = REGISTER_LOGIN_LIMIT,
                rateLimiter = rateLimiter,
                diagnosticsStore = diagnosticsStore,
            ) { request ->
                authService.register(
                    login = request.login,
                    password = request.password,
                )
            }
        }

        route.post("/api/v1/auth/login") {
            call.handleCredentialRequest(
                routeName = "login",
                directPeerLimit = LOGIN_DIRECT_PEER_LIMIT,
                loginLimit = LOGIN_LOGIN_LIMIT,
                rateLimiter = rateLimiter,
                diagnosticsStore = diagnosticsStore,
            ) { request ->
                authService.login(
                    login = request.login,
                    password = request.password,
                )
            }
        }
    }

    private suspend fun io.ktor.server.application.ApplicationCall.handleCredentialRequest(
        routeName: String,
        directPeerLimit: Int,
        loginLimit: Int,
        rateLimiter: AuthRateLimiter,
        diagnosticsStore: DiagnosticsStore?,
        action: (CredentialAuthRequest) -> com.bam.incomedy.server.auth.IssuedAuthSession,
    ) {
        val requestId = callId ?: "n/a"
        val directPeer = directPeerFingerprint()
        if (!rateLimiter.allow(key = "credentials_${routeName}_peer:$directPeer", limit = directPeerLimit, windowMs = 60_000L)) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = "auth.credentials.$routeName.rate_limited",
                status = HttpStatusCode.TooManyRequests.value,
                safeErrorCode = "rate_limited",
                metadata = mapOf("scope" to "peer"),
            )
            respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
            return
        }

        val request = receiveJsonBodyLimited<CredentialAuthRequest>(
            json = requestJson,
            maxBytes = MAX_REQUEST_BYTES,
        ).getOrElse { error ->
            if (error is PayloadTooLargeException) {
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "auth.credentials.$routeName.payload_too_large",
                    status = HttpStatusCode.PayloadTooLarge.value,
                    safeErrorCode = "payload_too_large",
                )
                respond(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorResponse("payload_too_large", "Request body is too large"),
                )
                return
            }
            diagnosticsStore?.recordCall(
                call = this,
                stage = "auth.credentials.$routeName.invalid_payload",
                status = HttpStatusCode.BadRequest.value,
                safeErrorCode = "bad_request",
            )
            respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid credential auth request"))
            return
        }

        val normalizedLoginHash = hashForRateLimit(request.login.trim().lowercase())
        if (!rateLimiter.allow(
                key = "credentials_${routeName}_login:$normalizedLoginHash",
                limit = loginLimit,
                windowMs = 60_000L,
            )
        ) {
            diagnosticsStore?.recordCall(
                call = this,
                stage = "auth.credentials.$routeName.rate_limited",
                status = HttpStatusCode.TooManyRequests.value,
                safeErrorCode = "rate_limited",
                metadata = mapOf("scope" to "login"),
            )
            respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limited", "Too many requests"))
            return
        }

        runCatching { action(request) }.fold(
            onSuccess = { auth ->
                logger.info("auth.credentials.{}.success requestId={} userId={}", routeName, requestId, auth.user.id)
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "auth.credentials.$routeName.success",
                    status = HttpStatusCode.OK.value,
                )
                respond(HttpStatusCode.OK, auth.toResponse())
            },
            onFailure = { error ->
                val status = failureStatus(routeName, error)
                val code = failureCode(routeName, error)
                logger.warn(
                    "auth.credentials.{}.failed requestId={} status={} reason={}",
                    routeName,
                    requestId,
                    status.value,
                    error.message ?: "unknown",
                )
                diagnosticsStore?.recordCall(
                    call = this,
                    stage = "auth.credentials.$routeName.failed",
                    status = status.value,
                    safeErrorCode = code,
                )
                respond(status, ErrorResponse(code, failureMessage(routeName, status)))
            },
        )
    }

    private fun hashForRateLimit(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun failureStatus(routeName: String, error: Throwable): HttpStatusCode {
        return when (error) {
            is InvalidCredentialLoginException,
            is WeakPasswordException -> HttpStatusCode.BadRequest
            is DuplicateCredentialLoginException -> HttpStatusCode.Conflict
            is InvalidCredentialsException -> if (routeName == "login") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
            else -> HttpStatusCode.BadRequest
        }
    }

    private fun failureCode(routeName: String, error: Throwable): String {
        return when (error) {
            is InvalidCredentialLoginException -> "invalid_login"
            is WeakPasswordException -> "weak_password"
            is DuplicateCredentialLoginException -> "login_already_exists"
            is InvalidCredentialsException -> if (routeName == "login") "invalid_credentials" else "invalid_registration"
            else -> "bad_request"
        }
    }

    private fun failureMessage(routeName: String, status: HttpStatusCode): String {
        return when {
            routeName == "login" && status == HttpStatusCode.Unauthorized -> "Invalid login or password"
            status == HttpStatusCode.Conflict -> "Login is already registered"
            status == HttpStatusCode.BadRequest -> "Invalid credential auth request"
            else -> "Credential auth failed"
        }
    }
}

@Serializable
data class CredentialAuthRequest(
    val login: String,
    val password: String,
)
