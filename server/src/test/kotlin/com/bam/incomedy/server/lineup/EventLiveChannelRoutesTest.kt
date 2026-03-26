package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.notifications.AnnouncementRoutes
import com.bam.incomedy.server.realtime.EventLiveChannelBroadcaster
import com.bam.incomedy.server.realtime.EventLiveChannelRoutes
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryAnnouncementRepository
import com.bam.incomedy.server.support.InMemoryComedianApplicationRepository
import com.bam.incomedy.server.support.InMemoryEventRepository
import com.bam.incomedy.server.support.InMemoryLineupRepository
import com.bam.incomedy.server.support.InMemoryUserRepository
import com.bam.incomedy.server.support.InMemoryVenueRepository
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
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
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Route-тесты public WebSocket live channel-а события. */
class EventLiveChannelRoutesTest {
    /** Проверяет, что public live channel отдает snapshot и live-state broadcast. */
    @Test
    fun `public event live channel streams initial snapshot and stage updates`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()
        val broadcaster = EventLiveChannelBroadcaster()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val event = seedPublishedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
        )
        lineupRepository.bindUser(comedian.id, comedian.displayName, comedian.username)
        configureRealtimeRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            broadcaster = broadcaster,
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
        val lineupEntryId = loadLineupEntryIds(event.id, ownerAccessToken).single()
        val websocketClient = createClient {
            install(WebSockets)
        }

        websocketClient.webSocket("/ws/events/${event.id}") {
            val initialPayload = receiveTextFrame()
            assertTrue(initialPayload.contains(""""type":"lineup.changed""""))
            assertTrue(initialPayload.contains(""""reason":"initial_snapshot""""))
            assertTrue(initialPayload.contains(comedian.displayName))

            val response = client.post("/api/v1/events/${event.id}/lineup/live-state") {
                header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
                contentType(ContentType.Application.Json)
                setBody("""{"entry_id":"$lineupEntryId","status":"on_stage"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val broadcastPayloads = listOf(receiveTextFrame(), receiveTextFrame())
            assertTrue(broadcastPayloads.any { it.contains(""""type":"lineup.changed"""") && it.contains("live_state_changed") })
            assertTrue(broadcastPayloads.any { it.contains(""""type":"stage.current_changed"""") && it.contains("on_stage") })
            assertTrue(broadcastPayloads.any { it.contains(comedian.displayName) })
        }
    }

    /** Проверяет, что approve заявки публикует lineup.changed в live channel. */
    @Test
    fun `approved application emits lineup changed event`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()
        val broadcaster = EventLiveChannelBroadcaster()

        val owner = ownerUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(comedian)
        val event = seedPublishedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
        )
        lineupRepository.bindUser(comedian.id, comedian.displayName, comedian.username)
        configureRealtimeRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            broadcaster = broadcaster,
        )

        val ownerAccessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val comedianAccessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val websocketClient = createClient {
            install(WebSockets)
        }

        websocketClient.webSocket("/ws/events/${event.id}") {
            val initialPayload = receiveTextFrame()
            assertTrue(initialPayload.contains(""""lineup":[]"""))

            val submitResponse = client.post("/api/v1/events/${event.id}/applications") {
                header(HttpHeaders.Authorization, "Bearer $comedianAccessToken")
                contentType(ContentType.Application.Json)
                setBody("""{"note":"Хочу в лайнап"}""")
            }
            assertEquals(HttpStatusCode.Created, submitResponse.status)
            val applicationId = Regex(""""id":"([^"]+)"""")
                .find(submitResponse.bodyAsText())
                ?.groupValues
                ?.get(1)
                ?: error("Application id missing")

            val approveResponse = client.patch("/api/v1/events/${event.id}/applications/$applicationId") {
                header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
                contentType(ContentType.Application.Json)
                setBody("""{"status":"approved"}""")
            }
            assertEquals(HttpStatusCode.OK, approveResponse.status)

            val approvalPayload = receiveTextFrame()
            assertTrue(approvalPayload.contains(""""type":"lineup.changed""""))
            assertTrue(approvalPayload.contains(""""reason":"application_approved""""))
            assertTrue(approvalPayload.contains(comedian.displayName))
        }
    }

    /** Проверяет, что live channel недоступного события закрывается понятным policy violation. */
    @Test
    fun `unavailable event live channel is rejected`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()
        val broadcaster = EventLiveChannelBroadcaster()

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
        configureRealtimeRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            broadcaster = broadcaster,
        )

        val websocketClient = createClient {
            install(WebSockets)
        }
        val session = websocketClient.webSocketSession("/ws/events/${event.id}")
        val closeReason = assertNotNull(session.closeReason.await())

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.code)
        assertEquals("Event live channel is unavailable", closeReason.message)
    }

    /** Проверяет, что новый organizer announcement публикуется в public live channel. */
    @Test
    fun `creating announcement emits announcement created event`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val announcementRepository = InMemoryAnnouncementRepository()
        val applicationRepository = InMemoryComedianApplicationRepository()
        val lineupRepository = InMemoryLineupRepository()
        val tokenService = tokenService()
        val broadcaster = EventLiveChannelBroadcaster()

        val owner = ownerUser()
        userRepository.putUser(owner)
        val event = seedPublishedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
        )
        configureRealtimeRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            announcementRepository = announcementRepository,
            applicationRepository = applicationRepository,
            lineupRepository = lineupRepository,
            tokenService = tokenService,
            broadcaster = broadcaster,
        )

        val ownerAccessToken = tokenService.issue(
            userId = owner.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val websocketClient = createClient {
            install(WebSockets)
        }

        websocketClient.webSocket("/ws/events/${event.id}") {
            val initialPayload = receiveTextFrame()
            assertTrue(initialPayload.contains(""""type":"lineup.changed""""))

            val response = client.post("/api/v1/events/${event.id}/announcements") {
                header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
                contentType(ContentType.Application.Json)
                setBody("""{"message":"Начинаем через 10 минут"}""")
            }
            assertEquals(HttpStatusCode.Created, response.status)

            val announcementPayload = receiveTextFrame()
            assertTrue(announcementPayload.contains(""""type":"announcement.created""""))
            assertTrue(announcementPayload.contains("Начинаем через 10 минут"))
            assertTrue(announcementPayload.contains(""""author_role":"organizer""""))
        }
    }

    /** Поднимает Ktor app с applications/lineup routes и public live-event channel. */
    private fun ApplicationTestBuilder.configureRealtimeRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        announcementRepository: InMemoryAnnouncementRepository,
        applicationRepository: InMemoryComedianApplicationRepository,
        lineupRepository: InMemoryLineupRepository,
        tokenService: JwtSessionTokenService,
        broadcaster: EventLiveChannelBroadcaster,
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
            install(ServerWebSockets)
            routing {
                ComedianApplicationsRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    comedianApplicationRepository = applicationRepository,
                    lineupRepository = lineupRepository,
                    eventLiveChannelBroadcaster = broadcaster,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
                LineupRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    lineupRepository = lineupRepository,
                    eventLiveChannelBroadcaster = broadcaster,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
                AnnouncementRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    announcementRepository = announcementRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                    eventLiveChannelBroadcaster = broadcaster,
                )
                EventLiveChannelRoutes.register(
                    route = this,
                    eventRepository = eventRepository,
                    lineupRepository = lineupRepository,
                    broadcaster = broadcaster,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }
    }

    /** Создает published public event для websocket сценариев. */
    private fun seedPublishedEvent(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        eventRepository: InMemoryEventRepository,
        owner: StoredUser,
    ): com.bam.incomedy.server.db.StoredOrganizerEvent {
        return seedEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            owner = owner,
            status = "published",
            visibility = "public",
        )
    }

    /** Создает событие с явным статусом и visibility для websocket сценариев. */
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

    /** Подает заявку комика и сразу переводит ее в approved, чтобы получить lineup entry. */
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

    /** Загружает текущий lineup события и возвращает все entry id в явном порядке. */
    private suspend fun ApplicationTestBuilder.loadLineupEntryIds(
        eventId: String,
        ownerAccessToken: String,
    ): List<String> {
        val lineupResponse = client.get("/api/v1/events/$eventId/lineup") {
            header(HttpHeaders.Authorization, "Bearer $ownerAccessToken")
        }
        assertEquals(HttpStatusCode.OK, lineupResponse.status)
        return Regex(""""id":"([^"]+)"""")
            .findAll(lineupResponse.bodyAsText())
            .map { it.groupValues[1] }
            .toList()
    }

    /** Считывает очередной text frame из websocket session. */
    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.receiveTextFrame(): String {
        return (incoming.receive() as Frame.Text).readText()
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
