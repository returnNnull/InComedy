package com.bam.incomedy.server

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.telegram.TelegramAuthRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthService
import com.bam.incomedy.server.auth.telegram.TelegramAuthVerifier
import com.bam.incomedy.server.config.AppConfig
import com.bam.incomedy.server.db.DatabaseFactory
import com.bam.incomedy.server.db.DatabaseSchemaInitializer
import com.bam.incomedy.server.db.PostgresTelegramUserRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.event.Level
import java.util.UUID

fun Application.module() {
    val config = AppConfig.fromEnv()
    val dataSource = DatabaseFactory.create(config.database)
    DatabaseSchemaInitializer.ensure(dataSource)

    val repository = PostgresTelegramUserRepository(dataSource)
    val verifier = TelegramAuthVerifier(
        botToken = config.telegram.botToken,
        maxAuthAgeSeconds = config.telegram.maxAuthAgeSeconds,
    )
    val tokenService = JwtSessionTokenService(config.jwt)
    val authService = TelegramAuthService(verifier, repository, tokenService)

    install(CallId) {
        retrieveFromHeader("X-Request-ID")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
        replyToHeader("X-Request-ID")
    }
    install(CallLogging) {
        level = Level.INFO
        mdc("requestId") { call ->
            call.callId
        }
    }
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", cause.message ?: "Unexpected server error"),
            )
        }
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/auth/telegram/callback") {
            call.respondText(
                text = telegramMobileBridgeHtml(),
                contentType = ContentType.Text.Html,
            )
        }

        TelegramAuthRoutes.register(this, authService)

        get("/") {
            call.respondText("InComedy server", ContentType.Text.Plain)
        }
    }
}

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)

private fun telegramMobileBridgeHtml(): String {
    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>InComedy Auth</title>
        </head>
        <body>
          <p>Returning to InComedy app...</p>
          <script>
            (function () {
              var search = window.location.search || "";
              var hash = window.location.hash || "";
              var parts = [];
              if (search.length > 1) parts.push(search.substring(1));
              if (hash.length > 1) parts.push(hash.substring(1));
              var query = parts.filter(Boolean).join("&");
              var target = "incomedy://auth/telegram";
              if (query.length > 0) target += "?" + query;
              window.location.replace(target);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
