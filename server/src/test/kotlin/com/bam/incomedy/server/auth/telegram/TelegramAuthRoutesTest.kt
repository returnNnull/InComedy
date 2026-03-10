package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramAuthRoutesTest {

    @Test
    fun `oversized telegram verify request returns 413`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            this.install(ContentNegotiation) {
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

    @Test
    fun `telegram verify rate limit is not bypassed by spoofed forwarded headers`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            this.install(ContentNegotiation) {
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

        repeat(20) { attempt ->
            val response = client.post("/api/v1/auth/telegram/verify") {
                contentType(ContentType.Application.Json)
                header("X-Forwarded-For", "203.0.113.${attempt + 1}")
                setBody(
                    """
                    {
                      "id": 123456789,
                      "first_name": "John",
                      "auth_date": 1700000000,
                      "hash": "${"a".repeat(64)}"
                    }
                    """.trimIndent(),
                )
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

        val limitedResponse = client.post("/api/v1/auth/telegram/verify") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", "198.51.100.200")
            setBody(
                """
                {
                  "id": 123456789,
                  "first_name": "John",
                  "auth_date": 1700000000,
                  "hash": "${"a".repeat(64)}"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.TooManyRequests, limitedResponse.status)
    }

    private fun testAuthService(): TelegramAuthService {
        val verifier = TelegramAuthVerifier(botToken = "test_bot_token", maxAuthAgeSeconds = 300L)
        val repository = InMemoryTelegramUserRepository()
        val tokenService = JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
        return TelegramAuthService(verifier, repository, tokenService)
    }
}
