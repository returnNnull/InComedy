package com.bam.incomedy.server.identity

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryUserRepository
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertTrue

class IdentityRoutesTest {

    @Test
    fun `active role can be switched when role is assigned`() = testApplication {
        val repository = InMemoryUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000010",
                    displayName = "Organizer User",
                    username = "organizer",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE, UserRole.ORGANIZER),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()

        environment {
            config = MapApplicationConfig()
        }
        application {
            this.install(ContentNegotiation) {
                json()
            }
            routing {
                IdentityRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    userRepository = repository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val accessToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000010",
            provider = AuthProvider.TELEGRAM,
        ).accessToken

        val response = client.post("/api/v1/me/active-role") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"role":"organizer"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""active_role":"organizer""""))
        assertTrue(body.contains(""""roles":["audience","organizer"]"""))
    }

    private fun tokenService(): JwtSessionTokenService {
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
