package com.bam.incomedy.server.events

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryEventRepository
import com.bam.incomedy.server.support.InMemoryUserRepository
import com.bam.incomedy.server.support.InMemoryVenueRepository
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
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Route-тесты organizer event management surface.
 */
class EventRoutesTest {
    /** Проверяет создание organizer event и последующую загрузку списка с frozen snapshot. */
    @Test
    fun `owner can create event and list it`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
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
        val template = venueRepository.createHallTemplate(
            venueId = venue.id,
            name = "Late Layout",
            status = "published",
            layoutJson = """
                {"stage":{"label":"Main Stage"},"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"},{"ref":"row-a-2","label":"2"}]}]}
            """.trimIndent(),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService,
        )

        val createResponse = client.post("/api/v1/events") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "workspace_id":"${workspace.id}",
                  "venue_id":"${venue.id}",
                  "hall_template_id":"${template.id}",
                  "title":"Late Night Standup",
                  "description":"Проверка event foundation",
                  "starts_at":"2026-03-20T19:00:00+03:00",
                  "doors_open_at":"2026-03-20T18:30:00+03:00",
                  "ends_at":"2026-03-20T21:00:00+03:00",
                  "currency":"RUB",
                  "visibility":"public"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Created, createResponse.status)
        val createBody = createResponse.bodyAsText()
        assertTrue(createBody.contains("Late Night Standup"))
        assertTrue(createBody.contains(""""venue_name":"Moscow Cellar""""))
        assertTrue(createBody.contains(""""source_template_name":"Late Layout""""))
        assertTrue(createBody.contains(""""sales_status":"closed""""))

        val listResponse = client.get("/api/v1/events") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Late Night Standup"))
        assertTrue(listBody.contains(""""hall_snapshot":"""))
    }

    /** Проверяет публикацию draft-события через отдельный publish route. */
    @Test
    fun `owner can publish existing draft event`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
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

        val storedEvent = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Night Standup",
            description = "Draft event",
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = java.time.OffsetDateTime.parse("2026-03-20T18:30:00+03:00"),
            endsAt = java.time.OffsetDateTime.parse("2026-03-20T21:00:00+03:00"),
            status = "draft",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = "template-1",
            sourceTemplateName = "Late Layout",
            snapshotJson = """{"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"}]}]}""",
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService,
        )

        val publishResponse = client.post("/api/v1/events/${storedEvent.id}/publish") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, publishResponse.status)
        val publishBody = publishResponse.bodyAsText()
        assertTrue(publishBody.contains(""""status":"published""""))
    }

    /** Проверяет, что snapshot события остается frozen после правки исходного hall template. */
    @Test
    fun `event snapshot stays frozen after source template changes`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
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
        val template = venueRepository.createHallTemplate(
            venueId = venue.id,
            name = "Late Layout",
            status = "published",
            layoutJson = """
                {"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"},{"ref":"row-a-2","label":"2"}]}],"blocked_seat_refs":["row-a-2"]}
            """.trimIndent(),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService,
        )

        val createResponse = client.post("/api/v1/events") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "workspace_id":"${workspace.id}",
                  "venue_id":"${venue.id}",
                  "hall_template_id":"${template.id}",
                  "title":"Snapshot Freeze Test",
                  "starts_at":"2026-03-20T19:00:00+03:00",
                  "currency":"RUB",
                  "visibility":"public"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        venueRepository.updateHallTemplate(
            templateId = template.id,
            name = "Late Layout v2",
            status = "published",
            layoutJson = """
                {"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"},{"ref":"row-a-2","label":"2"},{"ref":"row-a-3","label":"3"}]}]}
            """.trimIndent(),
        )

        val listResponse = client.get("/api/v1/events") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("Snapshot Freeze Test"))
        assertTrue(listBody.contains("row-a-2"))
        assertTrue(!listBody.contains("row-a-3"))
        assertTrue(listBody.contains("Late Layout"))
    }

    /** Поднимает тестовое Ktor-приложение только с organizer event routes. */
    private fun io.ktor.server.testing.ApplicationTestBuilder.configureEventRoutes(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        eventRepository: InMemoryEventRepository,
        tokenService: JwtSessionTokenService,
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            routing {
                EventRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    venueRepository = venueRepository,
                    eventRepository = eventRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }
    }

    /** Добавляет owner-пользователя в in-memory user repository. */
    private fun InMemoryUserRepository.putOwnerUser() {
        putUser(
            StoredUser(
                id = OWNER_ID,
                displayName = "Event Owner",
                username = "event_owner",
                photoUrl = null,
                sessionRevokedAt = null,
                linkedProviders = setOf(AuthProvider.PASSWORD),
                roles = setOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            ),
        )
    }

    /** Создает JWT token service для route-тестов organizer events. */
    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                secret = "event-routes-secret",
                issuer = "incomedy-test",
                accessTtlSeconds = 60 * 60L,
                refreshTtlSeconds = 30 * 24 * 60 * 60L,
            ),
        )
    }

    private companion object {
        const val OWNER_ID = "00000000-0000-0000-0000-000000000401"
    }
}
