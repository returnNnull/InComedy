package com.bam.incomedy.server.observability

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты operator-only diagnostics retrieval endpoint.
 */
class DiagnosticsRoutesTest {

    /** Подтверждает, что endpoint закрыт без корректного operator token. */
    @Test
    fun `diagnostics endpoint requires valid token`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                DiagnosticsRoutes.register(
                    route = this,
                    diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10),
                    accessToken = "secret-token",
                )
            }
        }

        val response = client.get("/api/v1/diagnostics/events")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /** Проверяет фильтрацию diagnostics events по request id и route prefix. */
    @Test
    fun `diagnostics endpoint returns filtered events`() = testApplication {
        val store = InMemoryDiagnosticsStore(retentionLimit = 10) { Instant.parse("2026-03-13T00:00:00Z") }
        store.record(
            DiagnosticsEventInput(
                requestId = "req-auth",
                method = "POST",
                route = "/api/v1/auth/telegram/verify",
                stage = "auth.telegram.verify.failed",
                status = 401,
                safeErrorCode = "telegram_auth_hash_invalid",
            ),
        )
        store.record(
            DiagnosticsEventInput(
                requestId = "req-workspaces",
                method = "GET",
                route = "/api/v1/workspaces",
                stage = "organizer.workspaces.list.success",
                status = 200,
            ),
        )

        environment {
            config = MapApplicationConfig()
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                DiagnosticsRoutes.register(
                    route = this,
                    diagnosticsStore = store,
                    accessToken = "secret-token",
                )
            }
        }

        val response = client.get("/api/v1/diagnostics/events?request_id=req-auth&route_prefix=/api/v1/auth") {
            header("X-Diagnostics-Token", "secret-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("req-auth"))
        assertTrue(body.contains("auth.telegram.verify.failed"))
        assertTrue(!body.contains("req-workspaces"))
    }
}
