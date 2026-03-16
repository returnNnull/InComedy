package com.bam.incomedy.server.venues

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryUserRepository
import com.bam.incomedy.server.support.InMemoryVenueRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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

class VenueRoutesTest {

    @Test
    fun `owner can create venue and list it`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = OWNER_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val workspace = userRepository.createWorkspace(
            ownerUserId = OWNER_ID,
            name = "Comedy Ops",
            slug = "comedy-ops",
        )

        configureVenueRoutes(userRepository, venueRepository, tokenService)

        val createResponse = client.post("/api/v1/venues") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "workspace_id":"${workspace.id}",
                  "name":"Moscow Cellar",
                  "city":"Moscow",
                  "address":"Tverskaya 1",
                  "timezone":"Europe/Moscow",
                  "capacity":120,
                  "description":"Клубный зал для вечерних шоу",
                  "contacts":[{"label":"Telegram","value":"@cellar"}]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createBody = createResponse.bodyAsText()
        assertTrue(createBody.contains("Moscow Cellar"))
        assertTrue(createBody.contains("Europe/Moscow"))

        val listResponse = client.get("/api/v1/venues") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Moscow Cellar"))
        assertTrue(listBody.contains(""""contacts":[{"label":"Telegram","value":"@cellar"}]"""))
    }

    @Test
    fun `owner can create update and clone hall template`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = OWNER_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val workspace = userRepository.createWorkspace(
            ownerUserId = OWNER_ID,
            name = "Comedy Ops",
            slug = "comedy-ops",
        )
        val venue = venueRepository.createVenue(
            workspaceId = workspace.id,
            name = "Moscow Cellar",
            city = "Moscow",
            address = "Tverskaya 1",
            timezone = "Europe/Moscow",
            capacity = 120,
            description = null,
            contactsJson = "[]",
        )

        configureVenueRoutes(userRepository, venueRepository, tokenService)

        val createTemplateResponse = client.post("/api/v1/venues/${venue.id}/hall-templates") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name":"Late Layout",
                  "status":"draft",
                  "layout":{
                    "stage":{"label":"Main Stage"},
                    "price_zones":[{"id":"vip","name":"VIP","default_price_minor":3000}],
                    "rows":[{"id":"row-a","label":"A","price_zone_id":"vip","seats":[
                      {"ref":"row-a-1","label":"1"},
                      {"ref":"row-a-2","label":"2"}
                    ]}],
                    "blocked_seat_refs":["row-a-2"]
                  }
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Created, createTemplateResponse.status)
        val templateId = Regex(""""id":"([^"]+)"""")
            .find(createTemplateResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("template id not found")

        val updateResponse = client.patch("/api/v1/hall-templates/$templateId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name":"Late Layout v2",
                  "status":"published",
                  "layout":{
                    "stage":{"label":"Main Stage"},
                    "rows":[{"id":"row-a","label":"A","seats":[
                      {"ref":"row-a-1","label":"1"},
                      {"ref":"row-a-2","label":"2"},
                      {"ref":"row-a-3","label":"3"}
                    ]}]
                  }
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateBody = updateResponse.bodyAsText()
        assertTrue(updateBody.contains(""""version":2"""))
        assertTrue(updateBody.contains("Late Layout v2"))

        val cloneResponse = client.post("/api/v1/hall-templates/$templateId/clone") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Late Layout Clone"}""")
        }

        assertEquals(HttpStatusCode.Created, cloneResponse.status)
        val cloneBody = cloneResponse.bodyAsText()
        assertTrue(cloneBody.contains("Late Layout Clone"))
        assertTrue(cloneBody.contains(""""status":"draft""""))
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.configureVenueRoutes(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        tokenService: JwtSessionTokenService,
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            routing {
                VenueRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    venueRepository = venueRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }
    }

    private fun InMemoryUserRepository.putOwnerUser() {
        putUser(
            StoredUser(
                id = OWNER_ID,
                displayName = "Venue Owner",
                username = "venue_owner",
                photoUrl = null,
                sessionRevokedAt = null,
                linkedProviders = setOf(AuthProvider.PASSWORD),
                roles = setOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            ),
        )
    }

    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                secret = "venue-routes-secret",
                issuer = "incomedy-test",
                accessTtlSeconds = 60 * 60L,
                refreshTtlSeconds = 30 * 24 * 60 * 60L,
            ),
        )
    }

    private companion object {
        const val OWNER_ID = "00000000-0000-0000-0000-000000000301"
    }
}
