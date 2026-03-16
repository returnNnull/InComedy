package com.bam.incomedy.server.ticketing

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryEventRepository
import com.bam.incomedy.server.support.InMemoryTicketingRepository
import com.bam.incomedy.server.support.InMemoryUserRepository
import io.ktor.client.request.delete
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Route-тесты ticketing foundation surface.
 */
class TicketingRoutesTest {
    /** Проверяет, что публичный опубликованный event inventory доступен аудитории и derived из snapshot-а. */
    @Test
    fun `audience user can list public event inventory`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedPublishedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""inventory_ref":"seat:seat-a-1""""))
        assertTrue(body.contains(""""inventory_ref":"zone:zone-1:slot:1""""))
        assertTrue(body.contains(""""inventory_ref":"table:table-1:seat:2""""))
        assertTrue(body.contains(""""status":"unavailable""""))
    }

    /** Проверяет, что одна и та же inventory unit не может быть зарезервирована дважды. */
    @Test
    fun `same inventory unit cannot be held twice`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedPublishedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            nowProvider = { NOW },
        )

        val firstResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        val secondResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }

        assertEquals(HttpStatusCode.Conflict, secondResponse.status)
        assertTrue(secondResponse.bodyAsText().contains("conflict"))
    }

    /** Проверяет, что просроченный hold освобождает inventory unit для нового reserve. */
    @Test
    fun `expired hold no longer blocks inventory`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val currentTime = mutableStateOf(NOW)
        val eventId = seedPublishedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            nowProvider = { currentTime.value },
            holdTtl = Duration.ofMinutes(1),
        )

        val createResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        currentTime.value = NOW.plusMinutes(2)

        val inventoryResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, inventoryResponse.status)
        assertTrue(inventoryResponse.bodyAsText().contains(""""status":"available""""))

        val secondCreateResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }
        assertEquals(HttpStatusCode.Created, secondCreateResponse.status)
    }

    /** Проверяет, что другой пользователь не может освободить чужой hold. */
    @Test
    fun `different user cannot release чужой hold`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
            putSecondAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val tokenService = tokenService()
        val firstAccessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val secondAccessToken = tokenService.issue(
            userId = SECOND_AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedPublishedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            nowProvider = { NOW },
        )

        val createResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $firstAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val holdId = Regex(""""id":"([^"]+)"""").find(createResponse.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("Hold id not found in response")

        val releaseResponse = client.delete("/api/v1/holds/$holdId") {
            header(HttpHeaders.Authorization, "Bearer $secondAccessToken")
        }

        assertEquals(HttpStatusCode.Forbidden, releaseResponse.status)
        assertTrue(releaseResponse.bodyAsText().contains("forbidden"))
    }

    /** Конфигурирует Ktor routing только с ticketing routes для route-тестов. */
    private fun ApplicationTestBuilder.configureTicketingRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        ticketingRepository: InMemoryTicketingRepository,
        tokenService: JwtSessionTokenService,
        nowProvider: () -> OffsetDateTime,
        holdTtl: Duration = Duration.ofMinutes(10),
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            routing {
                TicketingRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    workspaceRepository = userRepository,
                    eventRepository = eventRepository,
                    ticketingRepository = ticketingRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                    nowProvider = nowProvider,
                    holdTtl = holdTtl,
                )
            }
        }
    }

    /** Создает опубликованное событие с snapshot seat/zone/table inventory и event-local overrides. */
    private fun seedPublishedEvent(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        salesStatus: String,
    ): String {
        val workspace = userRepository.createWorkspace(
            ownerUserId = OWNER_ID,
            name = "Comedy Ops",
            slug = "comedy-ops",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = "00000000-0000-0000-0000-000000000611",
            venueName = "Moscow Cellar",
            title = "Late Night Standup",
            description = "Ticketing foundation",
            startsAt = NOW.plusDays(1),
            doorsOpenAt = NOW.plusDays(1).minusMinutes(30),
            endsAt = NOW.plusDays(1).plusHours(2),
            status = "published",
            salesStatus = salesStatus,
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = "template-1",
            sourceTemplateName = "Late Layout",
            snapshotJson = """
                {
                  "price_zones":[
                    {"id":"template-standard","name":"Standard","default_price_minor":1500},
                    {"id":"template-vip","name":"VIP","default_price_minor":3200}
                  ],
                  "zones":[
                    {"id":"zone-1","name":"Балкон","capacity":2,"price_zone_id":"template-standard","kind":"standing"}
                  ],
                  "rows":[
                    {
                      "id":"row-a",
                      "label":"A",
                      "price_zone_id":"template-standard",
                      "seats":[
                        {"ref":"seat-a-1","label":"1"},
                        {"ref":"seat-a-2","label":"2"}
                      ]
                    }
                  ],
                  "tables":[
                    {"id":"table-1","label":"VIP 1","seat_count":2,"price_zone_id":"template-vip"}
                  ],
                  "blocked_seat_refs":["seat-a-1"]
                }
            """.trimIndent(),
        )
        eventRepository.updateEvent(
            eventId = event.id,
            title = event.title,
            description = event.description,
            startsAt = event.startsAt,
            doorsOpenAt = event.doorsOpenAt,
            endsAt = event.endsAt,
            currency = event.currency,
            visibility = event.visibility,
            priceZones = listOf(
                StoredEventPriceZone(
                    id = "event-standard",
                    name = "Standard Event",
                    priceMinor = 1900,
                    currency = "RUB",
                    sourceTemplatePriceZoneId = "template-standard",
                ),
            ),
            pricingAssignments = emptyList(),
            availabilityOverrides = listOf(
                StoredEventAvailabilityOverride(
                    targetType = "zone",
                    targetRef = "zone-1",
                    availabilityStatus = "blocked",
                ),
            ),
        )
        return event.id
    }

    /** Добавляет owner-пользователя для создания workspace и event-а. */
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

    /** Добавляет audience-пользователя для ticketing flow. */
    private fun InMemoryUserRepository.putAudienceUser() {
        putUser(
            StoredUser(
                id = AUDIENCE_ID,
                displayName = "Audience User",
                username = "audience_user",
                photoUrl = null,
                sessionRevokedAt = null,
                linkedProviders = setOf(AuthProvider.PASSWORD),
                roles = setOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            ),
        )
    }

    /** Добавляет второго audience-пользователя для permission теста release route. */
    private fun InMemoryUserRepository.putSecondAudienceUser() {
        putUser(
            StoredUser(
                id = SECOND_AUDIENCE_ID,
                displayName = "Second Audience User",
                username = "audience_user_2",
                photoUrl = null,
                sessionRevokedAt = null,
                linkedProviders = setOf(AuthProvider.PASSWORD),
                roles = setOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            ),
        )
    }

    /** Создает JWT token service для ticketing route-тестов. */
    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                secret = "ticketing-routes-secret",
                issuer = "incomedy-test",
                accessTtlSeconds = 60 * 60L,
                refreshTtlSeconds = 30 * 24 * 60 * 60L,
            ),
        )
    }

    /** Простейший mutable carrier времени для route-тестов. */
    private data class MutableState<T>(
        var value: T,
    )

    /** Создает mutable carrier времени. */
    private fun <T> mutableStateOf(value: T): MutableState<T> {
        return MutableState(value)
    }

    private companion object {
        val NOW: OffsetDateTime = OffsetDateTime.parse("2026-03-20T10:00:00+03:00")
        const val OWNER_ID = "00000000-0000-0000-0000-000000000501"
        const val AUDIENCE_ID = "00000000-0000-0000-0000-000000000502"
        const val SECOND_AUDIENCE_ID = "00000000-0000-0000-0000-000000000503"
    }
}
