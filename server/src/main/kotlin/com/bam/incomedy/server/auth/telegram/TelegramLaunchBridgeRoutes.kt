package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.observability.DiagnosticsEventInput
import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.directPeerFingerprint
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.net.URI

/** First-party Telegram launch bridge page that starts browser auth from an approved InComedy origin. */
object TelegramLaunchBridgeRoutes {
    /** Registers the public launch page and optional safe client-side telemetry capture. */
    fun register(
        route: Route,
        authService: TelegramAuthService,
        diagnosticsStore: DiagnosticsStore? = null,
        rateLimiter: AuthRateLimiter? = null,
    ) {
        route.get("/auth/telegram/launch") {
            val state = call.request.queryParameters["state"].orEmpty()
            val officialAuthUrl = authService.resolveOfficialAuthUrl(state).getOrElse {
                diagnosticsStore?.recordCall(
                    call = call,
                    stage = "auth.telegram.launch.bridge.invalid_state",
                    status = HttpStatusCode.BadRequest.value,
                    safeErrorCode = "telegram_auth_state_invalid",
                    metadata = launchBridgeMetadata(
                        statePresent = state.isNotBlank(),
                        launchReady = false,
                    ),
                )
                call.respondText(
                    text = launchHtml(authUrl = null),
                    contentType = ContentType.Text.Html,
                    status = HttpStatusCode.BadRequest,
                )
                return@get
            }

            diagnosticsStore?.recordCall(
                call = call,
                stage = "auth.telegram.launch.bridge.ready",
                status = HttpStatusCode.OK.value,
                metadata = launchBridgeMetadata(
                    statePresent = state.isNotBlank(),
                    launchReady = true,
                ),
            )
            call.respondText(
                text = launchHtml(authUrl = officialAuthUrl),
                contentType = ContentType.Text.Html,
            )
        }

        route.get("/auth/telegram/launch/telemetry") {
            val stage = call.request.queryParameters["stage"].orEmpty()
                .trim()
                .takeIf(String::isNotBlank)
                ?: run {
                    call.respond(HttpStatusCode.NoContent, "")
                    return@get
                }
            val peer = call.directPeerFingerprint()
            val allowed = rateLimiter?.allow(
                key = "telegram_launch_bridge_telemetry:$peer",
                limit = LAUNCH_TELEMETRY_PEER_LIMIT,
                windowMs = 60_000L,
            ) ?: true
            if (allowed) {
                diagnosticsStore?.record(
                    DiagnosticsEventInput(
                        requestId = call.callId ?: "n/a",
                        method = call.request.httpMethod.value,
                        route = call.request.path(),
                        stage = "auth.telegram.launch.bridge.client_event",
                        status = HttpStatusCode.NoContent.value,
                        metadata = launchTelemetryMetadata(
                            stage = stage,
                            parameters = call.request.queryParameters,
                        ),
                    ),
                )
            }
            call.respond(HttpStatusCode.NoContent, "")
        }
    }

    /** Derives the public launch page URL from the registered Telegram callback URI. */
    fun launchUriFromRedirectUri(redirectUri: String): String {
        return URI(redirectUri).resolve("/auth/telegram/launch").toString()
    }

    /** Renders the static launch bridge page with either auth target or safe failure state. */
    fun launchHtml(authUrl: String?): String {
        val template = TelegramLaunchBridgeRoutes::class.java.classLoader
            .getResourceAsStream("static/telegram-launch.html")
            ?: error("Missing static/telegram-launch.html resource")
        return template.bufferedReader().use { it.readText() }
            .replace("__AUTH_URL__", authUrl.toJavascriptLiteral())
    }

    /** Captures safe low-cardinality metadata for the initial bridge response. */
    private fun launchBridgeMetadata(
        statePresent: Boolean,
        launchReady: Boolean,
    ): Map<String, String> {
        return mapOf(
            "state_present" to statePresent.toString(),
            "launch_ready" to launchReady.toString(),
        )
    }

    /** Captures safe client-side telemetry emitted by the launch bridge page. */
    private fun launchTelemetryMetadata(
        stage: String,
        parameters: Parameters,
    ): Map<String, String> {
        return mapOf(
            "client_stage" to stage.take(40).ifBlank { "unknown" },
            "is_android" to parameters["is_android"]?.toBooleanStrictOrNull().toString(),
            "has_auth_url" to parameters["has_auth_url"]?.toBooleanStrictOrNull().toString(),
            "redirect_mode" to parameters["redirect_mode"].orEmpty().take(24).ifBlank { "n/a" },
        )
    }

    /** Escapes an optional URL as a JavaScript string literal. */
    private fun String?.toJavascriptLiteral(): String {
        if (this == null) return "null"
        val builder = StringBuilder(length + 8)
        builder.append('"')
        for (char in this) {
            when (char) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '<' -> builder.append("\\u003C")
                '>' -> builder.append("\\u003E")
                '&' -> builder.append("\\u0026")
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }

    /** Per-peer cap for unauthenticated launch bridge telemetry events. */
    private const val LAUNCH_TELEMETRY_PEER_LIMIT = 60
}
