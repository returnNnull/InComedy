package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.observability.DiagnosticsQuery
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryUserRepository
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты diagnostics capture для session refresh route.
 */
class SessionDiagnosticsTest {

    /** Проверяет, что invalid refresh token сохраняется в diagnostics store с request id ответа. */
    @Test
    fun `invalid refresh token is recorded in diagnostics store`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)

        environment {
            config = MapApplicationConfig()
        }
        application {
            install(CallId) {
                generate { "223e4567-e89b-12d3-a456-426614174000" }
                replyToHeader("X-Request-ID")
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                SessionRoutes.register(
                    route = this,
                    tokenService = testTokenService(),
                    userRepository = InMemoryUserRepository(),
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refresh_token":"${"a".repeat(43)}"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val requestId = response.headers["X-Request-ID"]
        assertNotNull(requestId)
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "auth.refresh.invalid_token",
                limit = 1,
            ),
        ).single()
        assertEquals("invalid_refresh_token", event.safeErrorCode)
    }

    /** Создает тестовый token service для session diagnostics сценариев. */
    private fun testTokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
    }
}
