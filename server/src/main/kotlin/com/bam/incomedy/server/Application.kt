package com.bam.incomedy.server

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthService
import com.bam.incomedy.server.auth.telegram.TelegramAuthVerifier
import com.bam.incomedy.server.config.AppConfig
import com.bam.incomedy.server.db.DatabaseFactory
import com.bam.incomedy.server.db.DatabaseMigrationRunner
import com.bam.incomedy.server.db.PostgresTelegramUserRepository
import com.bam.incomedy.server.identity.IdentityRoutes
import com.bam.incomedy.server.observability.DiagnosticsConfig
import com.bam.incomedy.server.observability.DiagnosticsRoutes
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.observability.isValidRequestId
import com.bam.incomedy.server.organizer.WorkspaceRoutes
import com.bam.incomedy.server.security.AuthRateLimiter
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.security.RedisAuthRateLimiter
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
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import redis.clients.jedis.JedisPooled
import java.util.UUID

/** Конфигурирует и запускает HTTP-сервер приложения. */
fun Application.module() {
    val logger = LoggerFactory.getLogger("Application")
    val config = AppConfig.fromEnv()
    val diagnosticsConfig = DiagnosticsConfig.fromEnv()
    val dataSource = DatabaseFactory.create(config.database)
    DatabaseMigrationRunner.migrate(dataSource)

    val repository = PostgresTelegramUserRepository(dataSource)
    val verifier = TelegramAuthVerifier(
        botToken = config.telegram.botToken,
        maxAuthAgeSeconds = config.telegram.maxAuthAgeSeconds,
    )
    val tokenService = JwtSessionTokenService(config.jwt)
    val authService = TelegramAuthService(verifier, repository, tokenService)
    val rateLimiter: AuthRateLimiter = config.redis?.let { redis ->
        runCatching {
            RedisAuthRateLimiter(JedisPooled(redis.url))
        }.onSuccess {
            logger.info("security.rate_limiter.backend=redis")
        }.getOrElse { error ->
            logger.warn(
                "security.rate_limiter.backend=in_memory reason={}",
                error.message ?: "redis_init_failed",
            )
            InMemoryAuthRateLimiter()
        }
    } ?: InMemoryAuthRateLimiter().also {
        logger.info("security.rate_limiter.backend=in_memory")
    }
    val diagnosticsStore = diagnosticsConfig?.let { diagnostics ->
        logger.info("observability.diagnostics.enabled retentionLimit={}", diagnostics.retentionLimit)
        InMemoryDiagnosticsStore(retentionLimit = diagnostics.retentionLimit)
    } ?: run {
        logger.info("observability.diagnostics.disabled")
        null
    }

    install(CallId) {
        retrieveFromHeader("X-Request-ID")
        generate { UUID.randomUUID().toString() }
        verify(::isValidRequestId)
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
            logger.error(
                "server.unhandled_exception requestId={} reason={}",
                call.callId ?: "n/a",
                cause.message ?: "unknown",
                cause,
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("internal_error", "Unexpected server error"),
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

        TelegramAuthRoutes.register(
            route = this,
            authService = authService,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        SessionRoutes.register(
            route = this,
            tokenService = tokenService,
            userRepository = repository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        IdentityRoutes.register(
            route = this,
            tokenService = tokenService,
            userRepository = repository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        WorkspaceRoutes.register(
            route = this,
            tokenService = tokenService,
            userRepository = repository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        diagnosticsConfig?.let { diagnostics ->
            DiagnosticsRoutes.register(
                route = this,
                diagnosticsStore = diagnosticsStore ?: return@let,
                accessToken = diagnostics.accessToken,
            )
        }

        get("/") {
            call.respondText(
                text = telegramMobileBridgeHtml(),
                contentType = ContentType.Text.Html,
            )
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
