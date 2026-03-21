package com.bam.incomedy.server.events

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.observability.DiagnosticsQuery
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
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
import io.ktor.server.plugins.callid.CallId
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Route-тесты organizer event management surface.
 */
class EventRoutesTest {
    /** Проверяет, что anonymous audience видит только опубликованные public-события. */
    @Test
    fun `anonymous user can list published public events`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Moscow Late Show",
            city = "Moscow",
            startsAtIso = "2026-04-10T19:00:00+03:00",
            priceMinors = listOf(1500, 2500),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "SPB Showcase",
            city = "Saint Petersburg",
            startsAtIso = "2026-04-11T20:00:00+03:00",
            priceMinors = listOf(2900),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Private Show",
            city = "Moscow",
            startsAtIso = "2026-04-12T19:00:00+03:00",
            visibility = "private",
            priceMinors = listOf(1800),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Draft Show",
            city = "Moscow",
            startsAtIso = "2026-04-13T19:00:00+03:00",
            status = "draft",
            priceMinors = listOf(1700),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService(),
        )

        val response = client.get("/api/v1/public/events")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Moscow Late Show"))
        assertTrue(body.contains("SPB Showcase"))
        assertTrue(!body.contains("Private Show"))
        assertTrue(!body.contains("Draft Show"))
        assertTrue(body.contains(""""price_min_minor":1500"""))
        assertTrue(body.contains(""""price_max_minor":2500"""))
        assertTrue(!body.contains("workspace_id"))
        assertTrue(!body.contains("hall_snapshot"))
    }

    /** Проверяет детерминированную фильтрацию public discovery по city/date/price. */
    @Test
    fun `public event list filters by city date and price`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Matching Moscow Show",
            city = "Moscow",
            startsAtIso = "2026-04-10T19:00:00+03:00",
            priceMinors = listOf(1500, 2500),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Too Expensive Moscow Show",
            city = "Moscow",
            startsAtIso = "2026-04-10T20:00:00+03:00",
            priceMinors = listOf(3000),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Wrong Date Moscow Show",
            city = "Moscow",
            startsAtIso = "2026-04-11T19:00:00+03:00",
            priceMinors = listOf(2200),
        )
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Wrong City Show",
            city = "Kazan",
            startsAtIso = "2026-04-10T19:00:00+03:00",
            priceMinors = listOf(2200),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService(),
        )

        val response = client.get(
            "/api/v1/public/events?city=Moscow&date_from=2026-04-10&date_to=2026-04-10&price_min_minor=2000&price_max_minor=2600",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Matching Moscow Show"))
        assertTrue(!body.contains("Too Expensive Moscow Show"))
        assertTrue(!body.contains("Wrong Date Moscow Show"))
        assertTrue(!body.contains("Wrong City Show"))
    }

    /** Проверяет requestId-коррелированные diagnostics для публичного discovery route-а. */
    @Test
    fun `public event list success records diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply { putOwnerUser() }
        val venueRepository = InMemoryVenueRepository()
        val eventRepository = InMemoryEventRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        seedPublicEvent(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            title = "Diagnostics Show",
            city = "Moscow",
            startsAtIso = "2026-04-10T19:00:00+03:00",
            priceMinors = listOf(1900),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService(),
            diagnosticsStore = diagnosticsStore,
        )

        val response = client.get("/api/v1/public/events?city=Moscow&price_min_minor=1000")

        assertEquals(HttpStatusCode.OK, response.status)
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "event.public_list.success",
                limit = 1,
            ),
        ).single()
        assertEquals("1", event.metadata["count"])
        assertEquals("true", event.metadata["hasCityFilter"])
        assertEquals("false", event.metadata["hasDateFilter"])
        assertEquals("true", event.metadata["hasPriceFilter"])
    }

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

    /** Проверяет открытие продаж опубликованного события через отдельный sales route. */
    @Test
    fun `owner can open sales for published event`() = testApplication {
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
            title = "Published Event",
            description = "Ready for sales",
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
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

        val response = client.post("/api/v1/events/${storedEvent.id}/sales/open") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""status":"published""""))
        assertTrue(body.contains(""""sales_status":"open""""))
    }

    /** Проверяет паузу активных продаж через отдельный sales route. */
    @Test
    fun `owner can pause sales for on-sale event`() = testApplication {
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
            title = "On-sale Event",
            description = "Sales are active",
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "open",
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

        val response = client.post("/api/v1/events/${storedEvent.id}/sales/pause") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains(""""sales_status":"paused""""))
    }

    /** Проверяет отмену события с одновременным закрытием продаж. */
    @Test
    fun `owner can cancel published event`() = testApplication {
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
            title = "Cancelable Event",
            description = "Published and on sale",
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "open",
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

        val response = client.post("/api/v1/events/${storedEvent.id}/cancel") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""status":"canceled""""))
        assertTrue(body.contains(""""sales_status":"closed""""))
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

    /** Проверяет чтение и замену event-local overrides через detail/update routes. */
    @Test
    fun `owner can get and update event override details`() = testApplication {
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
            snapshotJson = """
                {"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"},{"ref":"row-a-2","label":"2"}]}],"zones":[{"id":"zone-left","name":"Left","capacity":20,"kind":"sector"}],"tables":[{"id":"table-1","label":"T1","seat_count":4}]}
            """.trimIndent(),
        )

        configureEventRoutes(
            userRepository = userRepository,
            venueRepository = venueRepository,
            eventRepository = eventRepository,
            tokenService = tokenService,
        )

        val updateResponse = client.patch("/api/v1/events/${storedEvent.id}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "title":"Late Night Standup Updated",
                  "description":"Override setup",
                  "starts_at":"2026-03-20T19:30:00+03:00",
                  "doors_open_at":"2026-03-20T18:45:00+03:00",
                  "ends_at":"2026-03-20T21:30:00+03:00",
                  "currency":"RUB",
                  "visibility":"public",
                  "price_zones":[
                    {
                      "id":"event-vip",
                      "name":"VIP",
                      "price_minor":3500,
                      "currency":"RUB",
                      "source_template_price_zone_id":"vip-template"
                    }
                  ],
                  "pricing_assignments":[
                    {
                      "target_type":"row",
                      "target_ref":"row-a",
                      "event_price_zone_id":"event-vip"
                    }
                  ],
                  "availability_overrides":[
                    {
                      "target_type":"seat",
                      "target_ref":"row-a-2",
                      "availability_status":"blocked"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateBody = updateResponse.bodyAsText()
        assertTrue(updateBody.contains("Late Night Standup Updated"))
        assertTrue(updateBody.contains(""""price_zones":[{"id":"event-vip""""))
        assertTrue(updateBody.contains(""""availability_overrides":[{"target_type":"seat""""))

        val getResponse = client.get("/api/v1/events/${storedEvent.id}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, getResponse.status)
        val getBody = getResponse.bodyAsText()
        assertTrue(getBody.contains("Late Night Standup Updated"))
        assertTrue(getBody.contains(""""target_ref":"row-a-2""""))
        assertTrue(getBody.contains(""""event_price_zone_id":"event-vip""""))
    }

    /** Проверяет safe validation ошибку при override на неизвестный snapshot target. */
    @Test
    fun `update event rejects unknown override target`() = testApplication {
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
            description = null,
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
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

        val response = client.patch("/api/v1/events/${storedEvent.id}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "title":"Late Night Standup",
                  "starts_at":"2026-03-20T19:00:00+03:00",
                  "currency":"RUB",
                  "visibility":"public",
                  "price_zones":[
                    {
                      "id":"event-main",
                      "name":"Main",
                      "price_minor":2000,
                      "currency":"RUB"
                    }
                  ],
                  "pricing_assignments":[
                    {
                      "target_type":"seat",
                      "target_ref":"missing-seat",
                      "event_price_zone_id":"event-main"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("snapshot target"))
    }

    /** Проверяет safe validation ошибку при открытии продаж для draft-события. */
    @Test
    fun `open sales rejects draft event`() = testApplication {
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
            title = "Draft Event",
            description = null,
            startsAt = java.time.OffsetDateTime.parse("2026-03-20T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
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

        val response = client.post("/api/v1/events/${storedEvent.id}/sales/open") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("опубликованного события"))
    }

    /** Поднимает тестовое Ktor-приложение только с organizer event routes. */
    private fun io.ktor.server.testing.ApplicationTestBuilder.configureEventRoutes(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        eventRepository: InMemoryEventRepository,
        tokenService: JwtSessionTokenService,
        diagnosticsStore: InMemoryDiagnosticsStore? = null,
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(CallId) {
                generate { UUID.randomUUID().toString() }
                replyToHeader("X-Request-ID")
            }
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
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }
    }

    /** Создает in-memory public event с venue metadata и необязательным price-range для discovery route-а. */
    private fun seedPublicEvent(
        userRepository: InMemoryUserRepository,
        venueRepository: InMemoryVenueRepository,
        eventRepository: InMemoryEventRepository,
        title: String,
        city: String,
        startsAtIso: String,
        status: String = "published",
        salesStatus: String = "open",
        visibility: String = "public",
        priceMinors: List<Int> = listOf(1900),
    ): String {
        val workspace = userRepository.createWorkspace(
            ownerUserId = OWNER_ID,
            name = "$title Workspace",
            slug = "workspace-${UUID.randomUUID()}",
        )
        val venue = venueRepository.createVenue(
            workspaceId = workspace.id,
            name = "$city Comedy Club",
            city = city,
            address = "$city Main Street 1",
            timezone = "Europe/Moscow",
            capacity = 120,
            description = null,
            contactsJson = "[]",
        )
        val event = eventRepository.createEvent(
            workspaceId = workspace.id,
            venueId = venue.id,
            venueName = venue.name,
            title = title,
            description = "$title description",
            startsAt = OffsetDateTime.parse(startsAtIso),
            doorsOpenAt = null,
            endsAt = null,
            status = status,
            salesStatus = salesStatus,
            currency = "RUB",
            visibility = visibility,
            sourceTemplateId = UUID.randomUUID().toString(),
            sourceTemplateName = "Public Layout",
            snapshotJson = """{"rows":[{"id":"row-a","label":"A","seats":[{"ref":"row-a-1","label":"1"}]}]}""",
        )
        if (priceMinors.isEmpty()) {
            return event.id
        }
        return requireNotNull(
            eventRepository.updateEvent(
                eventId = event.id,
                title = event.title,
                description = event.description,
                startsAt = event.startsAt,
                doorsOpenAt = event.doorsOpenAt,
                endsAt = event.endsAt,
                currency = event.currency,
                visibility = event.visibility,
                priceZones = priceMinors.mapIndexed { index, priceMinor ->
                    StoredEventPriceZone(
                        id = "zone-$index",
                        name = "Zone $index",
                        priceMinor = priceMinor,
                        currency = "RUB",
                    )
                },
                pricingAssignments = emptyList(),
                availabilityOverrides = emptyList(),
            ),
        ).id
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
