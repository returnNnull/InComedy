package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.config.VkIdConfig
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryUserRepository
import io.ktor.client.request.get
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
import kotlin.test.assertTrue

/**
 * HTTP-тесты маршрутов VK ID auth.
 */
class VkIdAuthRoutesTest {

    /** Проверяет, что backend возвращает browser launch URL для старта VK ID auth. */
    @Test
    fun `vk start returns launch url`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                VkIdAuthRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/api/v1/auth/vk/start")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("https://id.vk.ru/authorize?"))
        assertTrue(body.contains("\"state\":\""))
        assertTrue(body.contains("\"auth_url\":\""))
    }

    /** Проверяет, что без runtime-конфига VK ID маршруты возвращают `503`. */
    @Test
    fun `vk start returns 503 when auth is not configured`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                VkIdAuthRoutes.register(
                    route = this,
                    authService = null,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/api/v1/auth/vk/start")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    /** Проверяет, что oversized verify body отбрасывается до попытки обмена кода. */
    @Test
    fun `vk verify oversized payload returns 413`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                VkIdAuthRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.post("/api/v1/auth/vk/verify") {
            contentType(ContentType.Application.Json)
            setBody("x".repeat(5000))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    /** Проверяет, что неправильный `state` маппится в ожидаемый `400` без сетевого VK exchange. */
    @Test
    fun `vk verify invalid state returns 400`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                VkIdAuthRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.post("/api/v1/auth/vk/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "code": "vk_code",
                  "state": "invalid_state",
                  "device_id": "device123"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("vk_auth_state_invalid"))
    }

    /** Проверяет, что неожиданный JSON от VK помечается отдельным diagnostics-friendly error code. */
    @Test
    fun `vk verify invalid provider response returns 502`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        val authService = testAuthService(
            gateway = object : VkIdGateway {
                override fun buildAuthorizeUrl(
                    clientId: String,
                    redirectUri: String,
                    state: String,
                    codeChallenge: String,
                ): String {
                    return "https://id.vk.ru/authorize?client_id=$clientId&state=$state"
                }

                override fun exchangeAuthorizationCode(
                    clientId: String,
                    redirectUri: String,
                    code: String,
                    state: String,
                    deviceId: String,
                    codeVerifier: String,
                ): VkIdTokenResponse {
                    throw VkIdProviderResponseFormatException(
                        stage = VkIdProviderResponseStage.TOKEN_EXCHANGE,
                        message = "VK ID token response format is invalid",
                        cause = IllegalStateException("bad_json"),
                    )
                }

                override fun loadUserInfo(clientId: String, accessToken: String): VkIdUserInfoResponse {
                    error("Should not be called")
                }
            },
        )
        val validState = authService.createLaunchRequest().state
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                VkIdAuthRoutes.register(
                    route = this,
                    authService = authService,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.post("/api/v1/auth/vk/verify") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "code": "vk_code",
                  "state": "$validState",
                  "device_id": "device123"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertTrue(response.bodyAsText().contains("vk_auth_provider_response_invalid"))
    }

    /** Строит VK auth service с тестовым state codec и in-memory user repository. */
    private fun testAuthService(
        gateway: VkIdGateway? = null,
    ): VkIdAuthService {
        val config = VkIdConfig(
            clientId = "vk-test-client-id",
            redirectUri = "https://incomedy.ru/auth/vk/callback",
            androidClientId = "vk-android-client-id",
            androidRedirectUri = "vk123456://vk.ru/blank.html",
            scope = "vkid.personal_info",
            stateSecret = "vk-state-secret",
            stateTtlSeconds = 600L,
        )
        return VkIdAuthService(
            config = config,
            loginStateCodec = VkIdLoginStateCodec(
                secret = config.stateSecret,
                ttlSeconds = config.stateTtlSeconds,
                nowProvider = { Instant.parse("2026-03-14T20:00:00Z") },
            ),
            vkIdClient = gateway ?: VkIdClient(config),
            userRepository = InMemoryUserRepository(),
            tokenService = JwtSessionTokenService(
                JwtConfig(
                    issuer = "test",
                    secret = "0123456789abcdef0123456789abcdef",
                    accessTtlSeconds = 3600L,
                    refreshTtlSeconds = 86400L,
                ),
            ),
        )
    }
}
