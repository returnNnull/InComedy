package com.bam.incomedy.server

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionRoutes
import com.bam.incomedy.server.auth.credentials.Argon2PasswordHasher
import com.bam.incomedy.server.auth.credentials.CredentialsAuthRoutes
import com.bam.incomedy.server.auth.credentials.CredentialsAuthService
import com.bam.incomedy.server.auth.vk.VkIdAuthRoutes
import com.bam.incomedy.server.auth.vk.VkIdCallbackBridgeRoutes
import com.bam.incomedy.server.auth.vk.VkIdAuthService
import com.bam.incomedy.server.config.AppConfig
import com.bam.incomedy.server.db.DatabaseFactory
import com.bam.incomedy.server.db.DatabaseMigrationRunner
import com.bam.incomedy.server.db.PostgresComedianApplicationRepository
import com.bam.incomedy.server.db.PostgresEventRepository
import com.bam.incomedy.server.db.PostgresTicketingRepository
import com.bam.incomedy.server.db.PostgresVenueRepository
import com.bam.incomedy.server.db.PostgresUserRepository
import com.bam.incomedy.server.events.EventRoutes
import com.bam.incomedy.server.ios.AppleAppSiteAssociationRoutes
import com.bam.incomedy.server.identity.IdentityRoutes
import com.bam.incomedy.server.lineup.ComedianApplicationsRoutes
import com.bam.incomedy.server.observability.DiagnosticsConfig
import com.bam.incomedy.server.observability.DiagnosticsRoutes
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.observability.isValidRequestId
import com.bam.incomedy.server.organizer.WorkspaceRoutes
import com.bam.incomedy.server.payments.yookassa.YooKassaCheckoutGateway
import com.bam.incomedy.server.ticketing.TicketingRoutes
import com.bam.incomedy.server.venues.VenueRoutes
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

    val repository = PostgresUserRepository(dataSource)
    val venueRepository = PostgresVenueRepository(dataSource)
    val eventRepository = PostgresEventRepository(dataSource)
    val comedianApplicationRepository = PostgresComedianApplicationRepository(dataSource)
    val ticketingRepository = PostgresTicketingRepository(dataSource)
    val tokenService = JwtSessionTokenService(config.jwt)
    val passwordHasher = Argon2PasswordHasher()
    val credentialsAuthService = CredentialsAuthService(
        userRepository = repository,
        tokenService = tokenService,
        passwordHasher = passwordHasher,
    )
    val vkIdAuthService = config.vkId?.let {
        VkIdAuthService.create(
            config = it,
            userRepository = repository,
            tokenService = tokenService,
        )
    }
    val checkoutGateway = config.yooKassa?.let {
        logger.info("payments.checkout.provider=yookassa")
        YooKassaCheckoutGateway(it)
    } ?: run {
        logger.info("payments.checkout.provider=disabled")
        null
    }
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
        SessionRoutes.register(
            route = this,
            tokenService = tokenService,
            userRepository = repository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        CredentialsAuthRoutes.register(
            route = this,
            authService = credentialsAuthService,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        VkIdAuthRoutes.register(
            route = this,
            authService = vkIdAuthService,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        VkIdCallbackBridgeRoutes.register(
            route = this,
            diagnosticsStore = diagnosticsStore,
            rateLimiter = rateLimiter,
        )
        config.iosAssociatedDomains?.let { associatedDomains ->
            AppleAppSiteAssociationRoutes.register(
                route = this,
                config = associatedDomains,
            )
        }
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
            sessionUserRepository = repository,
            workspaceRepository = repository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        VenueRoutes.register(
            route = this,
            tokenService = tokenService,
            sessionUserRepository = repository,
            workspaceRepository = repository,
            venueRepository = venueRepository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        EventRoutes.register(
            route = this,
            tokenService = tokenService,
            sessionUserRepository = repository,
            workspaceRepository = repository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        ComedianApplicationsRoutes.register(
            route = this,
            tokenService = tokenService,
            sessionUserRepository = repository,
            workspaceRepository = repository,
            eventRepository = eventRepository,
            comedianApplicationRepository = comedianApplicationRepository,
            rateLimiter = rateLimiter,
            diagnosticsStore = diagnosticsStore,
        )
        TicketingRoutes.register(
            route = this,
            tokenService = tokenService,
            sessionUserRepository = repository,
            workspaceRepository = repository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            checkoutGateway = checkoutGateway,
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
                text = "<html><body><h1>InComedy Server</h1></body></html>",
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
