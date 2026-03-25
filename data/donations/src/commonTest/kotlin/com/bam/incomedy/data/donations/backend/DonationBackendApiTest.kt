package com.bam.incomedy.data.donations.backend

import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DonationBackendApiTest {
    @Test
    fun `get my payout profile returns null when backend envelope is empty`() = runTest {
        val api = DonationBackendApi(
            baseUrl = "https://example.com",
            httpClient = mockHttpClient(
                MockEngine { request ->
                    assertEquals("/api/v1/comedian/me/payout-profile", request.url.encodedPath)
                    respond(
                        content = """{"profile":null}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        )

        val result = api.getMyPayoutProfile(accessToken = "token")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `create donation intent maps backend dto to domain model`() = runTest {
        val api = DonationBackendApi(
            baseUrl = "https://example.com",
            httpClient = mockHttpClient(
                MockEngine { request ->
                    assertEquals("/api/v1/events/event-1/donations", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "id":"don-1",
                              "event_id":"event-1",
                              "event_title":"Late Show",
                              "comedian_user_id":"comedian-1",
                              "comedian_display_name":"Comic",
                              "donor_user_id":"audience-1",
                              "donor_display_name":"Fan",
                              "amount_minor":5000,
                              "currency":"RUB",
                              "message":"Спасибо",
                              "status":"created",
                              "created_at":"2026-03-25T18:00:00Z",
                              "updated_at":"2026-03-25T18:00:00Z",
                              "checkout_available":false
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        )

        val result = api.createDonationIntent(
            accessToken = "token",
            eventId = "event-1",
            comedianUserId = "comedian-1",
            amountMinor = 5000,
            currency = "RUB",
            message = "Спасибо",
            idempotencyKey = "idem-1",
        )

        val donation = result.getOrThrow()
        assertEquals("don-1", donation.id)
        assertEquals(DonationIntentStatus.CREATED, donation.status)
        assertEquals("Late Show", donation.eventTitle)
        assertEquals("Comic", donation.comedianDisplayName)
    }

    @Test
    fun `upsert payout profile maps backend dto to domain model`() = runTest {
        val api = DonationBackendApi(
            baseUrl = "https://example.com",
            httpClient = mockHttpClient(
                MockEngine { request ->
                    assertEquals("/api/v1/comedian/me/payout-profile", request.url.encodedPath)
                    assertEquals("PUT", request.method.value)
                    respond(
                        content = """
                            {
                              "id":"profile-1",
                              "user_id":"comedian-1",
                              "payout_provider":"manual_settlement",
                              "legal_type":"self_employed",
                              "beneficiary_ref":"+79990000000",
                              "verification_status":"pending",
                              "created_at":"2026-03-25T18:00:00Z",
                              "updated_at":"2026-03-25T18:00:00Z",
                              "status_updated_at":"2026-03-25T18:00:00Z"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        )

        val result = api.upsertMyPayoutProfile(
            accessToken = "token",
            legalType = PayoutLegalType.SELF_EMPLOYED,
            beneficiaryRef = "+79990000000",
        )

        val profile = result.getOrThrow()
        assertEquals("profile-1", profile.id)
        assertEquals(PayoutLegalType.SELF_EMPLOYED, profile.legalType)
        assertEquals("manual_settlement", profile.payoutProvider)
    }

    private fun mockHttpClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(backendJson)
            }
        }
    }
}
