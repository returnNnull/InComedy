package com.bam.incomedy.server.notifications

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryAnnouncementRepository
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
 * Route-тесты backend foundation slice-а organizer announcements/event feed.
 */
class AnnouncementRoutesTest {
    /** Проверяет, что organizer может публиковать announcement, а public feed отдает новые записи первыми. */
    @Test
    fun `organizer can create announcement and public feed lists newest first`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val venueRepository = InMemoryVenueRepository()
        var currentTime = OffsetDateTime.parse("2026-03-26T18:15:00+03:00")
        val announcementRepository = InMemoryAnnouncementRepository(
            nowProvider = {
                currentTime.also { currentTime = currentTime.plusMinutes(1) }
            },
        )
        val tokenService = tokenService()
        val owner = ownerUser()
        userRepository.putUser(owner)
        val event = seedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
            status = "published",
            visibility = "public",
        )
        configureAnnouncementRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            tokenService = tokenService,
        )

        val accessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val firstCreate = client.post("/api/v1/events/${event.id}/announcements") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Открываем двери через 15 минут"}""")
        }
        val secondCreate = client.post("/api/v1/events/${event.id}/announcements") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Начинаем через 5 минут"}""")
        }
        val feedResponse = client.get("/api/v1/public/events/${event.id}/announcements")

        assertEquals(HttpStatusCode.Created, firstCreate.status)
        assertEquals(HttpStatusCode.Created, secondCreate.status)
        assertEquals(HttpStatusCode.OK, feedResponse.status)
        assertTrue(secondCreate.bodyAsText().contains(""""author_role":"organizer""""))
        val feedBody = feedResponse.bodyAsText()
        assertTrue(feedBody.indexOf("Начинаем через 5 минут") < feedBody.indexOf("Открываем двери через 15 минут"))
    }

    /** Проверяет, что checker не может публиковать audience announcement. */
    @Test
    fun `checker cannot create announcement`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val venueRepository = InMemoryVenueRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val tokenService = tokenService()
        val owner = ownerUser()
        val checker = checkerUser()
        userRepository.putUser(owner)
        userRepository.putUser(checker)
        val event = seedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
            status = "published",
            visibility = "public",
        )
        val invitation = userRepository.createWorkspaceInvitation(
            workspaceId = event.workspaceId,
            invitedByUserId = owner.id,
            inviteeIdentifier = requireNotNull(checker.username),
            permissionRole = WorkspacePermissionRole.CHECKER,
        )
        userRepository.respondToWorkspaceInvitation(
            userId = checker.id,
            membershipId = invitation.membershipId,
            accept = true,
        )
        configureAnnouncementRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            tokenService = tokenService,
        )

        val accessToken = tokenService.issue(
            userId = checker.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.post("/api/v1/events/${event.id}/announcements") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Проверка soundcheck завершена"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("announcement_manage_forbidden"))
    }

    /** Проверяет, что public feed недоступен для не опубликованного audience event-а. */
    @Test
    fun `public announcement feed is unavailable for draft private event`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val venueRepository = InMemoryVenueRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val tokenService = tokenService()
        val owner = ownerUser()
        userRepository.putUser(owner)
        val event = seedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
            status = "draft",
            visibility = "private",
        )
        configureAnnouncementRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            tokenService = tokenService,
        )

        val response = client.get("/api/v1/public/events/${event.id}/announcements")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("Announcement feed is unavailable"))
    }

    /** Поднимает тестовый Ktor app только с announcement routes. */
    private fun ApplicationTestBuilder.configureAnnouncementRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        announcementRepository: InMemoryAnnouncementRepository,
        tokenService: JwtSessionTokenService,
    ) {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(CallId) {
                generate { "test-request-id" }
                replyToHeader("X-Request-ID")
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                AnnouncementRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    announcementRepository = announcementRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }
    }

    /** Создает событие с нужным статусом и visibility для announcement сценариев. */
    private fun seedEvent(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        eventRepository: InMemoryEventRepository,
        owner: StoredUser,
        status: String,
        visibility: String,
    ): com.bam.incomedy.server.db.StoredOrganizerEvent {
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
        return eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = status,
            salesStatus = "closed",
            currency = "RUB",
            visibility = visibility,
            sourceTemplateId = template.id,
            sourceTemplateName = template.name,
            snapshotJson = template.layoutJson,
        )
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

    /** Возвращает пользователя-checker для permission сценария. */
    private fun checkerUser(): StoredUser {
        return StoredUser(
            id = "00000000-0000-0000-0000-000000000303",
            displayName = "Checker User",
            username = "checker_user",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE),
            activeRole = UserRole.AUDIENCE,
        )
    }
}
