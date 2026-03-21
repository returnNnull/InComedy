package com.bam.incomedy.server.ticketing

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.observability.DiagnosticsQuery
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
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
import io.ktor.server.plugins.callid.CallId
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Route-тесты ticketing foundation surface.
 */
class TicketingRoutesTest {
    /** Проверяет, что anonymous audience может читать inventory опубликованного public event-а. */
    @Test
    fun `anonymous user can list public event inventory`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
            visibility = "public",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService(),
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/public/events/$eventId/inventory")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""inventory_ref":"seat:seat-a-1""""))
        assertTrue(body.contains(""""active_hold_id":null"""))
        assertTrue(body.contains(""""held_by_current_user":false"""))
    }

    /** Проверяет, что public inventory route пишет success diagnostics с requestId-корреляцией. */
    @Test
    fun `public inventory success records diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
            visibility = "public",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService(),
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/public/events/$eventId/inventory")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val inventoryCount = """"inventory_ref":"""".toRegex().findAll(body).count()
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.public_inventory.list.success",
                limit = 1,
            ),
        ).single()
        assertEquals(inventoryCount.toString(), event.metadata["count"])
        assertEquals("0", event.metadata["heldCount"])
    }

    /** Проверяет, что private event остается недоступным для anonymous public inventory route. */
    @Test
    fun `anonymous user cannot list private event inventory`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
        }
        val eventRepository = InMemoryEventRepository()
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
            visibility = "private",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = InMemoryTicketingRepository(),
            tokenService = tokenService(),
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/public/events/$eventId/inventory")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("not_found"))
    }

    /** Проверяет, что public inventory не раскрывает active hold id даже при существующем hold-е. */
    @Test
    fun `public inventory hides active hold ownership metadata`() = testApplication {
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
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
            visibility = "public",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            nowProvider = { NOW },
        )

        val holdResponse = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:seat-a-2"}""")
        }
        assertEquals(HttpStatusCode.Created, holdResponse.status)

        val response = client.get("/api/v1/public/events/$eventId/inventory")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(""""inventory_ref":"seat:seat-a-2""""))
        assertTrue(body.contains(""""status":"held""""))
        assertTrue(body.contains(""""active_hold_id":null"""))
        assertTrue(body.contains(""""hold_expires_at":null"""))
        assertTrue(body.contains(""""held_by_current_user":false"""))
    }

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
        val eventId = seedEvent(
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

    /** Проверяет, что повторный inventory GET не делает новый derived sync без event changes. */
    @Test
    fun `inventory read syncs only on bootstrap and after event update`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRevision = mutableStateOf(NOW)
        val eventRepository = InMemoryEventRepository { eventRevision.value }
        val ticketingRepository = InMemoryTicketingRepository()
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedEvent(
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

        val firstResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, firstResponse.status)
        assertTrue(firstResponse.bodyAsText().contains(""""price_minor":1900"""))
        assertEquals(1, ticketingRepository.synchronizeInventoryCallCount)

        val secondResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertEquals(1, ticketingRepository.synchronizeInventoryCallCount)

        val storedEvent = eventRepository.findEvent(eventId) ?: error("Event must remain readable")
        eventRevision.value = NOW.plusMinutes(5)
        eventRepository.updateEvent(
            eventId = eventId,
            title = storedEvent.title,
            description = storedEvent.description,
            startsAt = storedEvent.startsAt,
            doorsOpenAt = storedEvent.doorsOpenAt,
            endsAt = storedEvent.endsAt,
            currency = storedEvent.currency,
            visibility = storedEvent.visibility,
            priceZones = listOf(
                StoredEventPriceZone(
                    id = "event-standard",
                    name = "Standard Event",
                    priceMinor = 2100,
                    currency = "RUB",
                    sourceTemplatePriceZoneId = "template-standard",
                ),
            ),
            pricingAssignments = storedEvent.pricingAssignments,
            availabilityOverrides = storedEvent.availabilityOverrides,
        ) ?: error("Event update must succeed")

        val thirdResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, thirdResponse.status)
        assertTrue(thirdResponse.bodyAsText().contains(""""price_minor":2100"""))
        assertEquals(2, ticketingRepository.synchronizeInventoryCallCount)
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
        val eventId = seedEvent(
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

    /** Проверяет успешное создание checkout order и diagnostics для нового route-а. */
    @Test
    fun `audience user can create checkout order from active holds`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val ticketingRepository = InMemoryTicketingRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = ticketingRepository,
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val seatHoldId = createHold(
            accessToken = accessToken,
            eventId = eventId,
            inventoryRef = "seat:seat-a-2",
        )
        val tableHoldId = createHold(
            accessToken = accessToken,
            eventId = eventId,
            inventoryRef = "table:table-1:seat:1",
        )

        val orderResponse = client.post("/api/v1/events/$eventId/orders") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"hold_ids":["$seatHoldId","$tableHoldId"]}""")
        }

        assertEquals(HttpStatusCode.Created, orderResponse.status)
        val orderBody = orderResponse.bodyAsText()
        assertTrue(orderBody.contains(""""status":"awaiting_payment""""))
        assertTrue(orderBody.contains(""""total_minor":5100"""))
        assertTrue(orderBody.contains(""""inventory_ref":"seat:seat-a-2""""))
        assertTrue(orderBody.contains(""""inventory_ref":"table:table-1:seat:1""""))

        val inventoryResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, inventoryResponse.status)
        val inventoryBody = inventoryResponse.bodyAsText()
        assertTrue(inventoryBody.contains(""""inventory_ref":"seat:seat-a-2""""))
        assertTrue(inventoryBody.contains(""""status":"pending_payment""""))
        assertTrue(inventoryBody.contains(""""active_hold_id":null"""))

        val requestId = assertNotNull(orderResponse.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.order.create.success",
                limit = 1,
            ),
        ).single()
        assertEquals("2", event.metadata["lineCount"])
        assertEquals("5100", event.metadata["totalMinor"])
        assertEquals("awaiting_payment", event.metadata["status"])
    }

    /** Проверяет, что пользователь не может создать checkout order из чужого hold-а. */
    @Test
    fun `different user cannot create order from чужой hold`() = testApplication {
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
        val eventId = seedEvent(
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

        val holdId = createHold(
            accessToken = firstAccessToken,
            eventId = eventId,
            inventoryRef = "seat:seat-a-2",
        )

        val response = client.post("/api/v1/events/$eventId/orders") {
            header(HttpHeaders.Authorization, "Bearer $secondAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"hold_ids":["$holdId"]}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("forbidden"))
    }

    /** Проверяет, что истекший pending order автоматически освобождает inventory. */
    @Test
    fun `expired checkout order releases inventory back to availability`() = testApplication {
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
        val eventId = seedEvent(
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
            checkoutTtl = Duration.ofMinutes(1),
        )

        val holdId = createHold(
            accessToken = accessToken,
            eventId = eventId,
            inventoryRef = "seat:seat-a-2",
        )
        val orderResponse = client.post("/api/v1/events/$eventId/orders") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"hold_ids":["$holdId"]}""")
        }
        assertEquals(HttpStatusCode.Created, orderResponse.status)

        currentTime.value = NOW.plusMinutes(2)

        val inventoryResponse = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, inventoryResponse.status)
        val inventoryBody = inventoryResponse.bodyAsText()
        assertTrue(inventoryBody.contains(""""inventory_ref":"seat:seat-a-2""""))
        assertTrue(inventoryBody.contains(""""status":"available""""))
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
        val eventId = seedEvent(
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
        val eventId = seedEvent(
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

    /** Проверяет, что diagnostics различают отсутствие event-а и не смешивают его с другими 404. */
    @Test
    fun `missing event inventory records event-specific diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putAudienceUser()
        }
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = InMemoryEventRepository(),
            ticketingRepository = InMemoryTicketingRepository(),
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/events/$MISSING_EVENT_ID/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.event.not_found",
                limit = 1,
            ),
        ).single()
        assertEquals("event", event.metadata["resource"])
        assertEquals("missing", event.metadata["reason"])
    }

    /** Проверяет, что diagnostics различают unavailable event и сохраняют безопасную причину. */
    @Test
    fun `unavailable event inventory records event-unavailable diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            status = "draft",
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = InMemoryTicketingRepository(),
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val response = client.get("/api/v1/events/$eventId/inventory") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.event.unavailable",
                limit = 1,
            ),
        ).single()
        assertEquals("event", event.metadata["resource"])
        assertEquals("inventory_unavailable", event.metadata["reason"])
    }

    /** Проверяет, что diagnostics различают отсутствие inventory unit при создании hold-а. */
    @Test
    fun `missing inventory unit records inventory-specific diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putOwnerUser()
            putAudienceUser()
        }
        val eventRepository = InMemoryEventRepository()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val eventId = seedEvent(
            userRepository = userRepository,
            eventRepository = eventRepository,
            salesStatus = "open",
        )

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            ticketingRepository = InMemoryTicketingRepository(),
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val response = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"seat:missing"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.inventory.not_found",
                limit = 1,
            ),
        ).single()
        assertEquals("inventory", event.metadata["resource"])
        assertEquals("missing", event.metadata["reason"])
    }

    /** Проверяет, что diagnostics различают отсутствие hold-а при release route. */
    @Test
    fun `missing hold release records hold-specific diagnostics`() = testApplication {
        val userRepository = InMemoryUserRepository().apply {
            putAudienceUser()
        }
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val tokenService = tokenService()
        val accessToken = tokenService.issue(
            userId = AUDIENCE_ID,
            provider = AuthProvider.PASSWORD,
        ).accessToken

        configureTicketingRoutes(
            userRepository = userRepository,
            eventRepository = InMemoryEventRepository(),
            ticketingRepository = InMemoryTicketingRepository(),
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
            nowProvider = { NOW },
        )

        val response = client.delete("/api/v1/holds/$MISSING_HOLD_ID") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val requestId = assertNotNull(response.headers["X-Request-ID"])
        val event = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "ticketing.hold.not_found",
                limit = 1,
            ),
        ).single()
        assertEquals("hold", event.metadata["resource"])
        assertEquals("missing", event.metadata["reason"])
    }

    /** Конфигурирует Ktor routing только с ticketing routes для route-тестов. */
    private fun ApplicationTestBuilder.configureTicketingRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        ticketingRepository: InMemoryTicketingRepository,
        tokenService: JwtSessionTokenService,
        diagnosticsStore: InMemoryDiagnosticsStore? = null,
        nowProvider: () -> OffsetDateTime,
        holdTtl: Duration = Duration.ofMinutes(10),
        checkoutTtl: Duration = Duration.ofMinutes(10),
    ) {
        environment { config = MapApplicationConfig() }
        application {
            install(CallId) {
                generate { TEST_REQUEST_ID }
                replyToHeader("X-Request-ID")
            }
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
                    diagnosticsStore = diagnosticsStore,
                    nowProvider = nowProvider,
                    holdTtl = holdTtl,
                    checkoutTtl = checkoutTtl,
                )
            }
        }
    }

    /** Создает hold через HTTP route и возвращает его id для последующего checkout flow. */
    private suspend fun ApplicationTestBuilder.createHold(
        accessToken: String,
        eventId: String,
        inventoryRef: String,
    ): String {
        val response = client.post("/api/v1/events/$eventId/holds") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"inventory_ref":"$inventoryRef"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Regex(""""id":"([^"]+)"""").find(response.bodyAsText())
            ?.groupValues
            ?.get(1)
            ?: error("Hold id not found in response")
    }

    /** Создает тестовое событие с snapshot seat/zone/table inventory и event-local overrides. */
    private fun seedEvent(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        status: String = "published",
        salesStatus: String,
        visibility: String = "public",
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
            status = status,
            salesStatus = salesStatus,
            currency = "RUB",
            visibility = visibility,
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
        const val TEST_REQUEST_ID = "223e4567-e89b-12d3-a456-426614174000"
        const val OWNER_ID = "00000000-0000-0000-0000-000000000501"
        const val AUDIENCE_ID = "00000000-0000-0000-0000-000000000502"
        const val SECOND_AUDIENCE_ID = "00000000-0000-0000-0000-000000000503"
        const val MISSING_EVENT_ID = "00000000-0000-0000-0000-0000000009e1"
        const val MISSING_HOLD_ID = "00000000-0000-0000-0000-0000000009e2"
    }
}
