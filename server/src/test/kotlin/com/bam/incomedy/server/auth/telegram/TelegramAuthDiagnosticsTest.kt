package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты diagnostics capture для Telegram auth routes.
 */
class TelegramAuthDiagnosticsTest {

    /** Проверяет, что failed Telegram verify сохраняется в diagnostics store с тем же request id. */
    @Test
    fun `failed telegram verify is recorded in diagnostics store`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)

        environment {
            config = MapApplicationConfig()
        }
        application {
            install(CallId) {
                generate { "123e4567-e89b-12d3-a456-426614174000" }
                replyToHeader("X-Request-ID")
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                TelegramAuthRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }

        val response = client.post("/api/v1/auth/telegram/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "code": "oidc_code",
                  "state": "invalid_state"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val requestId = response.headers["X-Request-ID"]
        assertNotNull(requestId)
        val event = diagnosticsStore.query(
            com.bam.incomedy.server.observability.DiagnosticsQuery(
                requestId = requestId,
                stage = "auth.telegram.verify.failed",
                limit = 1,
            ),
        ).single()
        assertEquals("telegram_auth_state_invalid", event.safeErrorCode)
    }

    /** Создает тестовый auth service для route-level diagnostics проверки. */
    private fun testAuthService(): TelegramAuthService {
        val repository = InMemoryTelegramUserRepository()
        val tokenService = com.bam.incomedy.server.auth.session.JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
        return TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = "https://incomedy.ru/auth/telegram/callback",
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { Instant.parse("2026-03-13T08:00:00Z") },
            ),
            oidcClient = FakeTelegramDiagnosticsOidcGateway(),
            repository = repository,
            tokenService = tokenService,
            launchUri = "https://incomedy.ru/auth/telegram/launch",
        )
    }
}

/**
 * Fake OIDC gateway для diagnostics route-теста Telegram auth.
 */
private class FakeTelegramDiagnosticsOidcGateway : TelegramOidcGateway {
    /** Формирует предсказуемый launch URL для полноты тестового конфига. */
    override fun buildAuthorizationUrl(state: String, codeVerifier: String): String {
        return "https://oauth.telegram.org/auth?client_id=test-client-id&response_type=code&state=$state"
    }

    /** Verify не должен вызываться, потому что тест специально использует invalid state. */
    override fun exchangeAndVerify(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<VerifiedTelegramAuth> {
        return Result.success(
            VerifiedTelegramAuth(
                user = TelegramUser(
                    id = 10001L,
                    firstName = "Telegram User",
                    lastName = null,
                    username = "telegram_user",
                    photoUrl = null,
                ),
                assertionHash = "assertion-$code",
                replayExpiresAt = state.expiresAt,
            ),
        )
    }
}
