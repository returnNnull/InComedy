package com.bam.incomedy.server

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthRoutes
import com.bam.incomedy.server.auth.telegram.TelegramAuthService
import com.bam.incomedy.server.auth.telegram.TelegramCallbackBridgeRoutes
import com.bam.incomedy.server.auth.telegram.TelegramLaunchBridgeRoutes
import com.bam.incomedy.server.auth.telegram.TelegramLoginStateCodec
import com.bam.incomedy.server.auth.telegram.TelegramOidcClient
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
    val tokenService = JwtSessionTokenService(config.jwt)
    val loginStateCodec = TelegramLoginStateCodec(
        redirectUri = config.telegram.loginRedirectUri,
        secret = config.telegram.loginStateSecret,
        ttlSeconds = config.telegram.loginStateTtlSeconds,
    )
    val oidcClient = TelegramOidcClient(
        clientId = config.telegram.loginClientId,
        clientSecret = config.telegram.loginClientSecret,
        redirectUri = config.telegram.loginRedirectUri,
    )
    val authService = TelegramAuthService(
        loginStateCodec = loginStateCodec,
        oidcClient = oidcClient,
        repository = repository,
        tokenService = tokenService,
        launchUri = TelegramLaunchBridgeRoutes.launchUriFromRedirectUri(config.telegram.loginRedirectUri),
    )
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

        TelegramCallbackBridgeRoutes.register(
            route = this,
            diagnosticsStore = diagnosticsStore,
            rateLimiter = rateLimiter,
        )
        TelegramLaunchBridgeRoutes.register(
            route = this,
            authService = authService,
            diagnosticsStore = diagnosticsStore,
            rateLimiter = rateLimiter,
        )

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
                text = TelegramCallbackBridgeRoutes.bridgeHtml(),
                contentType = ContentType.Text.Html,
            )
        }
    }
}

/**
 * Унифицированная безопасная ошибка HTTP API.
 *
 * @property code Машинно-читаемый код ошибки.
 * @property message Безопасное сообщение для клиента.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
