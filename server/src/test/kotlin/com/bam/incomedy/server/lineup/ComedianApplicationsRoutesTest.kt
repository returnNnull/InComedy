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
    }

    /** Проверяет, что organizer review list скрыт от пользователя без owner/manager доступа. */
    @Test
    fun `comedian cannot list organizer applications`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
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

    /** Поднимает тестовый Ktor app только с applications routes. */
    private fun ApplicationTestBuilder.configureApplicationsRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        applicationRepository: InMemoryComedianApplicationRepository,
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
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }
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
