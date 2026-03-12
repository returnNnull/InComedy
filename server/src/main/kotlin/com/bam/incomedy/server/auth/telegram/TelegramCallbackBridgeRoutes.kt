package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.observability.DiagnosticsStore
import com.bam.incomedy.server.observability.recordCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Public HTTPS callback bridge for Telegram mobile auth handoff back into the app. */
object TelegramCallbackBridgeRoutes {
    /** Registers the public bridge page and optional bridge-hit diagnostics capture. */
    fun register(
        route: Route,
        diagnosticsStore: DiagnosticsStore? = null,
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
            "has_tg_auth_result" to parameters.contains("tgAuthResult").toString(),
            "has_id" to parameters.contains("id").toString(),
            "has_auth_date" to parameters.contains("auth_date").toString(),
            "has_hash" to parameters.contains("hash").toString(),
            "state_present" to parameters["state"].isNullOrBlank().not().toString(),
        )
    }
}
