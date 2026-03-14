package com.bam.incomedy.server.auth.vk

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

/** Public HTTPS callback bridge for VK mobile auth handoff back into the app. */
object VkIdCallbackBridgeRoutes {
    /** Registers the public bridge page and optional safe diagnostics capture. */
    fun register(
        route: Route,
        diagnosticsStore: DiagnosticsStore? = null,
        rateLimiter: AuthRateLimiter? = null,
    ) {
        route.get("/auth/vk/callback") {
            diagnosticsStore?.recordCall(
                call = call,
                stage = "auth.vk.callback.bridge.hit",
                status = HttpStatusCode.OK.value,
                metadata = vkCallbackBridgeMetadata(call.request.queryParameters),
            )
            call.respondText(
                text = bridgeHtml(),
                contentType = ContentType.Text.Html,
            )
        }

        route.get("/auth/vk/callback/telemetry") {
            val stage = call.request.queryParameters["stage"].orEmpty()
                .trim()
                .takeIf(String::isNotBlank)
                ?: run {
                    call.respond(HttpStatusCode.NoContent, "")
                    return@get
                }
            val peer = call.directPeerFingerprint()
            val allowed = rateLimiter?.allow(
                key = "vk_callback_bridge_telemetry:$peer",
                limit = CALLBACK_TELEMETRY_PEER_LIMIT,
                windowMs = 60_000L,
            ) ?: true
            if (allowed) {
                diagnosticsStore?.record(
                    DiagnosticsEventInput(
                        requestId = call.callId ?: "n/a",
                        method = call.request.httpMethod.value,
                        route = call.request.path(),
                        stage = "auth.vk.callback.bridge.client_event",
                        status = HttpStatusCode.NoContent.value,
                        metadata = vkCallbackTelemetryMetadata(
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
        val stream = VkIdCallbackBridgeRoutes::class.java.classLoader
            .getResourceAsStream("static/vk-callback.html")
            ?: error("Missing static/vk-callback.html resource")
        return stream.bufferedReader().use { it.readText() }
    }

    /** Collects a safe low-cardinality summary of the incoming VK callback request. */
    private fun vkCallbackBridgeMetadata(parameters: Parameters): Map<String, String> {
        return mapOf(
            "has_code" to parameters.contains("code").toString(),
            "has_device_id" to parameters.contains("device_id").toString(),
            "state_present" to parameters["state"].isNullOrBlank().not().toString(),
            "has_type" to parameters.contains("type").toString(),
        )
    }

    /** Collects safe low-cardinality metadata emitted by the bridge page JavaScript. */
    private fun vkCallbackTelemetryMetadata(
        stage: String,
        parameters: Parameters,
    ): Map<String, String> {
        return mapOf(
            "client_stage" to stage.take(40).ifBlank { "unknown" },
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
