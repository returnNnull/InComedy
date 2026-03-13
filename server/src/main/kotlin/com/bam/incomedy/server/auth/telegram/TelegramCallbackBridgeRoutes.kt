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

/** Public HTTPS callback bridge for Telegram mobile auth handoff back into the app. */
object TelegramCallbackBridgeRoutes {
    /** Registers the public bridge page and optional bridge-hit diagnostics capture. */
    fun register(
        route: Route,
        diagnosticsStore: DiagnosticsStore? = null,
        rateLimiter: AuthRateLimiter? = null,
    ) {
        route.get("/auth/telegram/callback") {
            diagnosticsStore?.recordCall(
                call = call,
                stage = "auth.telegram.callback.bridge.hit",
                status = HttpStatusCode.OK.value,
                metadata = telegramCallbackBridgeMetadata(call.request.queryParameters),
            )
            call.respondText(
                text = telegramMobileBridgeHtml(),
                contentType = ContentType.Text.Html,
            )
        }

        route.get("/auth/telegram/callback/telemetry") {
            val stage = call.request.queryParameters["stage"].orEmpty()
                .trim()
                .takeIf(String::isNotBlank)
                ?: run {
                    call.respond(HttpStatusCode.NoContent, "")
                    return@get
                }
            val peer = call.directPeerFingerprint()
            val allowed = rateLimiter?.allow(
                key = "telegram_callback_bridge_telemetry:$peer",
                limit = CALLBACK_TELEMETRY_PEER_LIMIT,
                windowMs = 60_000L,
            ) ?: true
            if (allowed) {
                diagnosticsStore?.record(
                    DiagnosticsEventInput(
                        requestId = call.callId ?: "n/a",
                        method = call.request.httpMethod.value,
                        route = call.request.path(),
                        stage = "auth.telegram.callback.bridge.client_event",
                        status = HttpStatusCode.NoContent.value,
                        metadata = telegramCallbackTelemetryMetadata(
                            stage = stage,
                            parameters = call.request.queryParameters,
                        ),
                    ),
                )
            }
            call.respond(HttpStatusCode.NoContent, "")
        }
    }

    /** Returns the static callback bridge HTML shipped with the server artifact. */
    fun bridgeHtml(): String {
        return telegramMobileBridgeHtml()
    }

    /** Loads the static callback bridge HTML shipped with the server artifact. */
    private fun telegramMobileBridgeHtml(): String {
        val stream = TelegramCallbackBridgeRoutes::class.java.classLoader
            .getResourceAsStream("static/telegram-callback.html")
            ?: error("Missing static/telegram-callback.html resource")
        return stream.bufferedReader().use { it.readText() }
    }

    /** Collects a safe low-cardinality summary of the incoming Telegram callback request. */
    private fun telegramCallbackBridgeMetadata(parameters: Parameters): Map<String, String> {
        return mapOf(
            "has_code" to parameters.contains("code").toString(),
            "has_tg_auth_result" to parameters.contains("tgAuthResult").toString(),
            "has_id" to parameters.contains("id").toString(),
            "has_auth_date" to parameters.contains("auth_date").toString(),
            "has_hash" to parameters.contains("hash").toString(),
            "state_present" to parameters["state"].isNullOrBlank().not().toString(),
        )
    }

    /** Collects safe low-cardinality metadata emitted by the bridge page JavaScript. */
    private fun telegramCallbackTelemetryMetadata(
        stage: String,
        parameters: Parameters,
    ): Map<String, String> {
        val safeStage = stage.take(40).ifBlank { "unknown" }
        return mapOf(
            "client_stage" to safeStage,
            "is_android" to parameters["is_android"]?.toBooleanStrictOrNull().toString(),
            "has_query" to parameters["has_query"]?.toBooleanStrictOrNull().toString(),
            "has_fragment" to parameters["has_fragment"]?.toBooleanStrictOrNull().toString(),
            "has_payload" to parameters["has_payload"]?.toBooleanStrictOrNull().toString(),
            "launch_mode" to parameters["launch_mode"].orEmpty().take(24).ifBlank { "n/a" },
        )
    }

    /** Per-peer cap for unauthenticated callback bridge telemetry events. */
    private const val CALLBACK_TELEMETRY_PEER_LIMIT = 60
}
