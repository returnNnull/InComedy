package com.bam.incomedy.server.donations

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
import com.bam.incomedy.server.support.InMemoryDonationRepository
import com.bam.incomedy.server.support.InMemoryEventRepository
import com.bam.incomedy.server.support.InMemoryLineupRepository
import com.bam.incomedy.server.support.InMemoryUserRepository
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Route-тесты backend foundation slice-а donations/payouts.
 */
class DonationRoutesTest {
    @Test
    fun `comedian can upsert payout profile`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val lineupRepository = InMemoryLineupRepository()
        val donationRepository = InMemoryDonationRepository()
        val tokenService = tokenService()
        val comedian = comedianUser()
        userRepository.putUser(comedian)
        donationRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
        )
        configureDonationRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
            donationRepository = donationRepository,
            tokenService = tokenService,
        )

        val accessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.put("/api/v1/comedian/me/payout-profile") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"legal_type":"self_employed","beneficiary_ref":"+79990000000"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("manual_settlement"))
        assertTrue(body.contains("self_employed"))
        assertTrue(body.contains("pending"))
    }

    @Test
    fun `audience cannot upsert comedian payout profile`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val lineupRepository = InMemoryLineupRepository()
        val donationRepository = InMemoryDonationRepository()
        val tokenService = tokenService()
        val audience = audienceUser()
        userRepository.putUser(audience)
        configureDonationRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
            donationRepository = donationRepository,
            tokenService = tokenService,
        )

        val accessToken = tokenService.issue(
            userId = audience.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.put("/api/v1/comedian/me/payout-profile") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"legal_type":"self_employed","beneficiary_ref":"+79990000000"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("comedian_role_required"))
    }

    @Test
    fun `audience can create donation intent for verified comedian and retry idempotently`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val lineupRepository = InMemoryLineupRepository()
        val donationRepository = InMemoryDonationRepository()
        val tokenService = tokenService()
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 20)
        val owner = ownerUser()
        val audience = audienceUser()
        val comedian = comedianUser()
        userRepository.putUser(owner)
        userRepository.putUser(audience)
        userRepository.putUser(comedian)
        donationRepository.bindUser(userId = audience.id, displayName = audience.displayName)
        donationRepository.bindUser(userId = comedian.id, displayName = comedian.displayName)
        donationRepository.seedVerifiedPayoutProfile(userId = comedian.id)
        val eventId = seedEvent(eventRepository = eventRepository)
        donationRepository.bindEvent(eventId = eventId, title = "Late Show")
        lineupRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
            username = comedian.username,
        )
        lineupRepository.createLineupEntry(
            eventId = eventId,
            comedianUserId = comedian.id,
            applicationId = null,
            status = com.bam.incomedy.server.db.LineupEntryStatus.DRAFT,
            notes = null,
        )
        configureDonationRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
            donationRepository = donationRepository,
            tokenService = tokenService,
            diagnosticsStore = diagnosticsStore,
        )

        val accessToken = tokenService.issue(
            userId = audience.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val requestBody = """{"comedian_user_id":"${comedian.id}","amount_minor":5000,"currency":"RUB","message":"Спасибо за сет","idempotency_key":"donation-intent-1"}"""
        val firstResponse = client.post("/api/v1/events/$eventId/donations") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
        val secondResponse = client.post("/api/v1/events/$eventId/donations") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.Created, firstResponse.status)
        assertEquals(HttpStatusCode.OK, secondResponse.status)
        val firstId = Regex(""""id":"([^"]+)"""").find(firstResponse.bodyAsText())?.groupValues?.get(1)
        val secondId = Regex(""""id":"([^"]+)"""").find(secondResponse.bodyAsText())?.groupValues?.get(1)
        assertEquals(firstId, secondId)
        assertTrue(firstResponse.bodyAsText().contains(""""status":"created""""))
        val requestId = assertNotNull(firstResponse.headers["X-Request-ID"])
        val diagnostics = diagnosticsStore.query(
            DiagnosticsQuery(
                requestId = requestId,
                stage = "audience.donation.create.success",
                limit = 1,
            ),
        ).single()
        assertEquals("created", diagnostics.metadata["donationStatus"])
    }

    @Test
    fun `comedian can list received donations`() = testApplication {
        val userRepository = InMemoryUserRepository()
        val eventRepository = InMemoryEventRepository()
        val lineupRepository = InMemoryLineupRepository()
        val donationRepository = InMemoryDonationRepository()
        val tokenService = tokenService()
        val comedian = comedianUser()
        val audience = audienceUser()
        userRepository.putUser(comedian)
        userRepository.putUser(audience)
        donationRepository.bindUser(userId = comedian.id, displayName = comedian.displayName)
        donationRepository.bindUser(userId = audience.id, displayName = audience.displayName)
        donationRepository.seedVerifiedPayoutProfile(userId = comedian.id)
        donationRepository.bindEvent(eventId = EVENT_ID, title = "Late Show")
        lineupRepository.bindUser(
            userId = comedian.id,
            displayName = comedian.displayName,
            username = comedian.username,
        )
        lineupRepository.createLineupEntry(
            eventId = EVENT_ID,
            comedianUserId = comedian.id,
            applicationId = null,
            status = com.bam.incomedy.server.db.LineupEntryStatus.DRAFT,
            notes = null,
        )
        donationRepository.createDonationIntent(
            eventId = EVENT_ID,
            comedianUserId = comedian.id,
            donorUserId = audience.id,
            amountMinor = 2500,
            currency = "RUB",
            message = "Удачи!",
            status = com.bam.incomedy.domain.donations.DonationIntentStatus.CREATED,
            idempotencyKey = "seed-donation-1",
        )
        configureDonationRoutes(
            userRepository = userRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
            donationRepository = donationRepository,
            tokenService = tokenService,
        )

        val accessToken = tokenService.issue(
            userId = comedian.id,
            provider = AuthProvider.PASSWORD,
        ).accessToken
        val response = client.get("/api/v1/comedian/me/donations") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(comedian.id))
        assertTrue(body.contains(audience.displayName))
        assertTrue(body.contains("2500"))
    }

    private fun ApplicationTestBuilder.configureDonationRoutes(
        userRepository: InMemoryUserRepository,
        eventRepository: InMemoryEventRepository,
        lineupRepository: InMemoryLineupRepository,
        donationRepository: InMemoryDonationRepository,
        tokenService: JwtSessionTokenService,
        diagnosticsStore: InMemoryDiagnosticsStore? = null,
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
                DonationRoutes.register(
                    route = this,
                    tokenService = tokenService,
                    sessionUserRepository = userRepository,
                    eventRepository = eventRepository,
                    lineupRepository = lineupRepository,
                    donationRepository = donationRepository,
                    rateLimiter = InMemoryAuthRateLimiter(),
                    diagnosticsStore = diagnosticsStore,
                )
            }
        }
    }

    private fun seedEvent(
        eventRepository: InMemoryEventRepository,
    ): String {
        return eventRepository.createEvent(
            workspaceId = WORKSPACE_ID,
            venueId = VENUE_ID,
            venueName = "Moscow Cellar",
            title = "Late Show",
            description = "Test event",
            startsAt = OffsetDateTime.parse("2026-04-10T19:00:00+03:00"),
            doorsOpenAt = null,
            endsAt = null,
            status = "published",
            salesStatus = "open",
            currency = "RUB",
            visibility = "public",
            sourceTemplateId = TEMPLATE_ID,
            sourceTemplateName = "Late Layout",
            snapshotJson = """{"stage":{"label":"Main Stage"},"rows":[]}""",
        ).id
    }

    private fun ownerUser(): StoredUser {
        return StoredUser(
            id = OWNER_ID,
            displayName = "Owner",
            username = "owner",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE, UserRole.ORGANIZER),
            activeRole = UserRole.ORGANIZER,
        )
    }

    private fun audienceUser(): StoredUser {
        return StoredUser(
            id = AUDIENCE_ID,
            displayName = "Audience",
            username = "audience",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE),
            activeRole = UserRole.AUDIENCE,
        )
    }

    private fun comedianUser(): StoredUser {
        return StoredUser(
            id = COMEDIAN_ID,
            displayName = "Comedian",
            username = "comedian",
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = setOf(AuthProvider.PASSWORD),
            roles = setOf(UserRole.AUDIENCE, UserRole.COMEDIAN),
            activeRole = UserRole.COMEDIAN,
        )
    }

    private fun tokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                issuer = "https://incomedy.test",
                secret = "test-secret-test-secret-test-secret",
                accessTtlSeconds = 1_800L,
                refreshTtlSeconds = 604_800L,
            ),
        )
    }

    private companion object {
        private const val OWNER_ID = "00000000-0000-0000-0000-000000000101"
        private const val AUDIENCE_ID = "00000000-0000-0000-0000-000000000102"
        private const val COMEDIAN_ID = "00000000-0000-0000-0000-000000000103"
        private const val WORKSPACE_ID = "00000000-0000-0000-0000-000000000104"
        private const val VENUE_ID = "00000000-0000-0000-0000-000000000105"
        private const val TEMPLATE_ID = "00000000-0000-0000-0000-000000000106"
        private const val EVENT_ID = "00000000-0000-0000-0000-000000000107"
    }
}
