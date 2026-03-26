package com.bam.incomedy.data.notifications.backend

import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
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
import kotlin.test.assertTrue

class NotificationBackendApiTest {
    @Test
    fun `list public event announcements maps backend dto to domain model`() = runTest {
        val api = NotificationBackendApi(
            baseUrl = "https://example.com",
            httpClient = mockHttpClient(
                MockEngine { request ->
                    assertEquals("/api/v1/public/events/event-1/announcements", request.url.encodedPath)
                    respond(
                        content = """
                            {
                              "announcements": [
                                {
                                  "id": "announcement-1",
                                  "event_id": "event-1",
                                  "message": "Начинаем через 10 минут",
                                  "author_role": "organizer",
                                  "created_at": "2026-03-26T18:20:00+03:00"
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        )

        val result = api.listPublicEventAnnouncements(eventId = "event-1")

        val announcement = result.getOrThrow().single()
        assertEquals("announcement-1", announcement.id)
        assertEquals("event-1", announcement.eventId)
        assertEquals(EventAnnouncementAuthorRole.ORGANIZER, announcement.authorRole)
        assertEquals("Начинаем через 10 минут", announcement.message)
    }

    @Test
    fun `create event announcement maps backend dto to domain model`() = runTest {
        val api = NotificationBackendApi(
            baseUrl = "https://example.com",
            httpClient = mockHttpClient(
                MockEngine { request ->
                    assertEquals("/api/v1/events/event-1/announcements", request.url.encodedPath)
                    assertEquals("POST", request.method.value)
                    assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = """
                            {
                              "id": "announcement-2",
                              "event_id": "event-1",
                              "message": "Старт задерживается на 5 минут",
                              "author_role": "host",
                              "created_at": "2026-03-26T18:25:00+03:00"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ),
        )

        val result = api.createEventAnnouncement(
            accessToken = "token",
            eventId = "event-1",
            message = "Старт задерживается на 5 минут",
        )

        val announcement = result.getOrThrow()
        assertEquals("announcement-2", announcement.id)
        assertEquals(EventAnnouncementAuthorRole.HOST, announcement.authorRole)
        assertEquals("Старт задерживается на 5 минут", announcement.message)
        assertTrue(result.isSuccess)
    }

    private fun mockHttpClient(engine: MockEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(backendJson)
            }
        }
    }
}
