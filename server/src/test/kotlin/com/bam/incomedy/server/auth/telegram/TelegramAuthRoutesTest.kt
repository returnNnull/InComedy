package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HTTP-тесты Telegram auth start/verify маршрутов.
 */
class TelegramAuthRoutesTest {

    /** Oversized verify body должен по-прежнему отбрасываться с 413. */
    @Test
    fun `oversized telegram verify request returns 413`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                TelegramAuthRoutes.register(this, testAuthService(), InMemoryAuthRateLimiter())
            }
        }

        val response = client.post("/api/v1/auth/telegram/verify") {
            contentType(ContentType.Application.Json)
            setBody("x".repeat(5000))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    /** Telegram auth start должен возвращать launch URL по публичному GET endpoint. */
    @Test
    fun `telegram start returns launch url`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                TelegramAuthRoutes.register(this, testAuthService(), InMemoryAuthRateLimiter())
            }
        }

        val response = client.get("/api/v1/auth/telegram/start")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.bodyAsText().contains("/auth/telegram/launch?state="))
    }

    /** Rate limit verify должен оставаться независимым от spoofed forwarded headers. */
    @Test
    fun `telegram verify rate limit is not bypassed by spoofed forwarded headers`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                TelegramAuthRoutes.register(
                    this,
                    testAuthService(),
                    InMemoryAuthRateLimiter(nowMillis = { 0L }),
                )
            }
        }

        repeat(600) { attempt ->
            val response = client.post("/api/v1/auth/telegram/verify") {
                contentType(ContentType.Application.Json)
                header("X-Forwarded-For", "203.0.113.${attempt + 1}")
                setBody(
                    """
                    {
                      "code": "oidc_code_$attempt",
                      "state": "invalid_state"
                    }
                    """.trimIndent(),
                )
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        val limitedResponse = client.post("/api/v1/auth/telegram/verify") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", "198.51.100.200")
            setBody(
                """
                {
                  "code": "oidc_code_limit",
                  "state": "invalid_state"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.TooManyRequests, limitedResponse.status)
    }

    /** Строит Telegram auth service с fake OIDC gateway для route-тестов. */
    private fun testAuthService(): TelegramAuthService {
        return TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = "https://incomedy.ru/auth/telegram/callback",
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { Instant.parse("2026-03-13T08:00:00Z") },
            ),
            oidcClient = FakeTelegramRoutesOidcGateway(),
            repository = InMemoryTelegramUserRepository(),
            tokenService = JwtSessionTokenService(
                JwtConfig(
                    issuer = "test",
                    secret = "0123456789abcdef0123456789abcdef",
                    accessTtlSeconds = 3600L,
                    refreshTtlSeconds = 86400L,
                ),
            ),
            launchUri = "https://incomedy.ru/auth/telegram/launch",
        )
    }
}

/**
 * Fake Telegram OIDC gateway для route-тестов, не требующий реальных сетевых вызовов.
 */
private class FakeTelegramRoutesOidcGateway : TelegramOidcGateway {
    /** Формирует предсказуемый Telegram auth URL для start endpoint тестов. */
    override fun buildAuthorizationUrl(state: String, codeVerifier: String): String {
        return "https://oauth.telegram.org/auth?client_id=test-client-id&response_type=code&state=$state"
    }

    /** Verify здесь не должен вызываться в happy-path route-тестах с invalid state. */
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
