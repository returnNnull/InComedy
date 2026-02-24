package com.bam.incomedy.server

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionRoutes
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
        SessionRoutes.register(
            route = this,
            tokenService = tokenService,
            userRepository = repository,
        )

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
    val stream = Application::class.java.classLoader.getResourceAsStream("static/telegram-callback.html")
        ?: error("Missing static/telegram-callback.html resource")
    return stream.bufferedReader().use { it.readText() }
}
