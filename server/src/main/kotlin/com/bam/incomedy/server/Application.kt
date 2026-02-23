package com.bam.incomedy.server

import com.bam.incomedy.server.auth.telegram.TelegramAuthRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthService
import com.bam.incomedy.server.auth.telegram.TelegramAuthVerifier
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
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
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.event.Level

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

    install(CallLogging) {
        level = Level.INFO
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
