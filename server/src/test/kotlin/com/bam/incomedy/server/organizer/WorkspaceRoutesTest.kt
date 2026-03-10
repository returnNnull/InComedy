package com.bam.incomedy.server.organizer

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceRoutesTest {

    @Test
    fun `workspace can be created and listed by owner`() = testApplication {
        val repository = InMemoryTelegramUserRepository().apply {
            putUser(
                StoredUser(
                    id = "00000000-0000-0000-0000-000000000020",
                    displayName = "Workspace Owner",
                    username = "owner",
                    photoUrl = null,
                    sessionRevokedAt = null,
                    linkedProviders = setOf(AuthProvider.TELEGRAM),
                    roles = setOf(UserRole.AUDIENCE),
                    activeRole = UserRole.AUDIENCE,
                ),
            )
        }
        val tokenService = tokenService()

        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                WorkspaceRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    userRepository = repository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val accessToken = tokenService.issue(
            userId = "00000000-0000-0000-0000-000000000020",
            provider = AuthProvider.TELEGRAM,
        ).accessToken

        val createResponse = client.post("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Comedy Cellar Moscow"}""")
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createdBody = createResponse.bodyAsText()
        assertTrue(createdBody.contains(""""permission_role":"owner""""))

        val listResponse = client.get("/api/v1/workspaces") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Comedy Cellar Moscow"))
        assertTrue(listBody.contains(""""permission_role":"owner""""))
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
