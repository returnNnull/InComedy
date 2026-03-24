package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.observability.DiagnosticsQuery
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryComedianApplicationRepository
import com.bam.incomedy.server.support.InMemoryEventRepository
import com.bam.incomedy.server.support.InMemoryLineupRepository
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
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Route-тесты backend foundation slice-а для заявок комиков.
 */
class ComedianApplicationsRoutesTest {
    /** Проверяет happy path: комик подает заявку, organizer видит ее и меняет статус. */
    @Test
    fun `comedian can submit application and organizer can review it`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val workspace = userRepository.createWorkspace(
            ownerUserId = owner.id,
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
            layoutJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
        configureApplicationsRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
        )

        val comedianAccessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val submitResponse = client.post("/api/v1/events/${event.id}/applications") {
            header(HttpHeaders.Authorization, "Bearer $comedianAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Хочу выступить с новым сетом"}""")
        }

        assertEquals(HttpStatusCode.Created, submitResponse.status)
        applicationRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
            username = comedian.username,
        )
        val applicationId = Regex(""""id":"([^"]+)"""")
            .find(submitResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("Application id missing in response")

        val ownerAccessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val listResponse = client.get("/api/v1/events/${event.id}/applications") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
        }

        assertEquals(HttpStatusCode.OK, listResponse.status)
        val listBody = listResponse.bodyAsText()
        assertTrue(listBody.contains("comedian_user_id"))
        assertTrue(listBody.contains(comedian.id))
        assertTrue(listBody.contains("submitted"))

        val updateResponse = client.patch("/api/v1/events/${event.id}/applications/$applicationId") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"approved"}""")
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        assertTrue(updateResponse.bodyAsText().contains("approved"))

        applicationRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
            username = comedian.username,
        )
        lineupRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
            username = comedian.username,
        )
        val lineupResponse = client.get("/api/v1/events/${event.id}/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
        }

        assertEquals(HttpStatusCode.OK, lineupResponse.status)
        val lineupBody = lineupResponse.bodyAsText()
        assertTrue(lineupBody.contains(applicationId))
        assertTrue(lineupBody.contains(""""order_index":1"""))
        assertTrue(lineupBody.contains("draft"))
    }

    /** Проверяет, что organizer review list скрыт от пользователя без owner/manager доступа. */
    @Test
    fun `comedian cannot list organizer applications`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val workspace = userRepository.createWorkspace(
            ownerUserId = owner.id,
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
            layoutJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
        configureApplicationsRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
        )

        val comedianAccessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.get("/api/v1/events/${event.id}/applications") {
            header(HttpHeaders.Authorization, "Bearer $comedianAccessToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("workspace_scope_not_found"))
    }

    /** Проверяет diagnostics запись для успешной подачи заявки. */
    @Test
    fun `submit success records diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val workspace = userRepository.createWorkspace(
            ownerUserId = owner.id,
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
            layoutJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
        configureApplicationsRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
        )

        val comedianAccessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.post("/api/v1/events/${event.id}/applications") {
            header(HttpHeaders.Authorization, "Bearer $comedianAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Иду на open mic"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val requestId = response.headers["X-Request-ID"] ?: error("Missing request id")
        val diagnostics = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "comedian.application.submit.success",
                limit = 5,
            ),
        )
        assertEquals(1, diagnostics.size)
        assertEquals("submitted", diagnostics.single().metadata["status"])
    }

    /** Проверяет, что organizer может переставить lineup, а diagnostics сохраняют request-id-correlated запись. */
    @Test
    fun `organizer can reorder lineup and diagnostics keep request id`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()

        val owner = ownerUser()
        val firstComedian = comedianUser()
        val secondComedian = StoredUser(
            id = "00000000-0000-0000-0000-000000000303",
            displayName = "Second Comedian",
            username = "second_comedian",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE, UserRole.COMEDIAN),
            activeRole = UserRole.COMEDIAN,
        )
        userRepository.putUser(owner)
        userRepository.putUser(firstComedian)
        userRepository.putUser(secondComedian)
        val workspace = userRepository.createWorkspace(
            ownerUserId = owner.id,
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
            layoutJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
        configureApplicationsRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
        )

        val ownerAccessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val firstApplicationId = submitAndApprove(
            comedian = firstComedian,
            eventId = event.id,
            ownerAccessToken = ownerAccessToken,
            tokenService = tokenService,
        )
        val secondApplicationId = submitAndApprove(
            comedian = secondComedian,
            eventId = event.id,
            ownerAccessToken = ownerAccessToken,
            tokenService = tokenService,
        )
        lineupRepository.bindUser(firstComedian.id, firstComedian.displayName, firstComedian.username)
        lineupRepository.bindUser(secondComedian.id, secondComedian.displayName, secondComedian.username)

        val initialLineupResponse = client.get("/api/v1/events/${event.id}/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
        }
        assertEquals(HttpStatusCode.OK, initialLineupResponse.status)
        val lineupEntries = Regex(""""id":"([^"]+)"""")
            .findAll(initialLineupResponse.bodyAsText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(2, lineupEntries.size)
        assertTrue(initialLineupResponse.bodyAsText().contains(firstApplicationId))
        assertTrue(initialLineupResponse.bodyAsText().contains(secondApplicationId))

        val reorderResponse = client.patch("/api/v1/events/${event.id}/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "entries": [
                    {"entry_id":"${lineupEntries[1]}","order_index":1},
                    {"entry_id":"${lineupEntries[0]}","order_index":2}
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, reorderResponse.status)
        val reorderBody = reorderResponse.bodyAsText()
        assertTrue(reorderBody.contains(""""order_index":1"""))
        val requestId = reorderResponse.headers["X-Request-ID"] ?: error("Missing request id")
        val diagnostics = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "organizer.lineup.reorder.success",
                limit = 5,
            ),
        )
        assertEquals(1, diagnostics.size)
        assertEquals("2", diagnostics.single().metadata["count"])
    }

    /** Проверяет, что reorder request с неполным набором lineup entries отклоняется. */
    @Test
    fun `reorder lineup rejects partial payload`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val workspace = userRepository.createWorkspace(
            ownerUserId = owner.id,
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
            layoutJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "closed",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
        configureApplicationsRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
        )

        val ownerAccessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        submitAndApprove(
            comedian = comedian,
            eventId = event.id,
            ownerAccessToken = ownerAccessToken,
            tokenService = tokenService,
        )
        val lineupResponse = client.get("/api/v1/events/${event.id}/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
        }
        val lineupEntryId = Regex(""""id":"([^"]+)"""")
            .find(lineupResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("Lineup entry id missing")

        val response = client.patch("/api/v1/events/${event.id}/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"entries":[{"entry_id":"$lineupEntryId","order_index":2}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("bad_request"))
    }

    /** Поднимает тестовый Ktor app только с applications routes. */
    private fun ApplicationTestBuilder.configureApplicationsRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        applicationRepository: InMemoryComedianApplicationRepository,
        lineupRepository: InMemoryLineupRepository,
        tokenService: JwtSessionTokenService,
        diagnosticsStore: InMemoryDiagnosticsStore? = null,
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(CallId) {
                generate { "test-request-id" }
                replyToHeader("X-Request-ID")
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                ComedianApplicationsRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    comedianApplicationRepository = applicationRepository,
                    lineupRepository = lineupRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
                LineupRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    lineupRepository = lineupRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }
    }

    /** Подает заявку комика и сразу переводит ее в `approved`, чтобы получить lineup entry. */
    private suspend fun ApplicationTestBuilder.submitAndApprove(
        comedian: StoredUser,
        eventId: String,
        ownerAccessToken: String,
        tokenService: JwtSessionTokenService,
    ): String {
        val comedianAccessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val submitResponse = client.post("/api/v1/events/$eventId/applications") {
            header(HttpHeaders.Authorization, "Bearer $comedianAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Новый сет"}""")
        }
        val applicationId = Regex(""""id":"([^"]+)"""")
            .find(submitResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("Application id missing")
        val approveResponse = client.patch("/api/v1/events/$eventId/applications/$applicationId") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"approved"}""")
        }
        assertEquals(HttpStatusCode.OK, approveResponse.status)
        return applicationId
    }

    /** Создает token service для isolated route tests. */
    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                issuer = "incomedy-test",
                secret = "01234567890123456789012345678901",
                accessTtlSeconds = 3_600,
                refreshTtlSeconds = 86_400,
            ),
        )
    }

    /** Возвращает organizer owner пользователя для тестов. */
    private fun ownerUser(): StoredUser {
        return StoredUser(
            id = "00000000-0000-0000-0000-000000000101",
            displayName = "Owner User",
            username = "owner_user",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE, UserRole.ORGANIZER),
            activeRole = UserRole.ORGANIZER,
        )
    }

    /** Возвращает пользователя-комика для тестов submit route-а. */
    private fun comedianUser(): StoredUser {
        return StoredUser(
            id = "00000000-0000-0000-0000-000000000202",
            displayName = "Comedian User",
            username = "comedian_user",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE, UserRole.COMEDIAN),
            activeRole = UserRole.COMEDIAN,
        )
    }
}
