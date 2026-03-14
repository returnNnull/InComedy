package com.bam.incomedy.server.auth.credentials

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.client.request.setBody
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialsAuthRoutesTest {

    @Test
    fun `register creates password session`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                CredentialsAuthRoutes.register(
                    route = this,
                    authService = CredentialsAuthService(
                        userRepository = InMemoryTelegramUserRepository(),
                        tokenService = testTokenService(),
                        passwordHasher = FakePasswordHasher(),
                    ),
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"tester","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""provider":"password""""))
        assertTrue(body.contains(""""linked_providers":["password"]"""))
    }

    @Test
    fun `login rejects invalid credentials`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                CredentialsAuthRoutes.register(
                    route = this,
                    authService = CredentialsAuthService(
                        userRepository = InMemoryTelegramUserRepository(),
                        tokenService = testTokenService(),
                        passwordHasher = FakePasswordHasher(),
                    ),
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"missing","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

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

private class FakePasswordHasher : PasswordHasher {
    override fun hash(password: CharArray): String {
        return "hash:${password.concatToString()}"
    }

    override fun verify(password: CharArray, passwordHash: String): Boolean {
        return hash(password) == passwordHash
    }
}
