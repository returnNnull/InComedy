package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests the Telegram HTTPS callback bridge response and safe diagnostics capture. */
class TelegramCallbackBridgeRoutesTest {

    /** The callback bridge should serve fallback HTML and record a safe bridge-hit event. */
    @Test
    fun `callback bridge returns html and records diagnostics`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)
        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                TelegramCallbackBridgeRoutes.register(
                    route = this,
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/auth/telegram/callback?code=oidc_code&state=test_state")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Tap Open app to finish signing in."))
        assertTrue(body.contains("history.replaceState"))
        assertTrue(body.contains("/auth/telegram/callback/telemetry"))

        val events = diagnosticsStore.query(
            com.bam.incomedy.server.observability.DiagnosticsQuery(
                routePrefix = "/auth/telegram/callback",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.callback.bridge.hit", events.single().stage)
        assertEquals("true", events.single().metadata["has_code"])
        assertEquals("false", events.single().metadata["has_id"])
        assertEquals("false", events.single().metadata["has_auth_date"])
        assertEquals("false", events.single().metadata["has_hash"])
        assertEquals("true", events.single().metadata["state_present"])
    }

    /** The callback bridge telemetry endpoint should record safe client-side stages. */
    @Test
    fun `callback bridge telemetry records client stage`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)
        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                TelegramCallbackBridgeRoutes.register(
                    route = this,
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get(
            "/auth/telegram/callback/telemetry" +
                "?stage=open_app_clicked&is_android=true&has_query=false&has_fragment=true&has_payload=true&launch_mode=manual_button",
        )

        assertEquals(HttpStatusCode.NoContent, response.status)
        val events = diagnosticsStore.query(
            com.bam.incomedy.server.observability.DiagnosticsQuery(
                routePrefix = "/auth/telegram/callback/telemetry",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.callback.bridge.client_event", events.single().stage)
        assertEquals("open_app_clicked", events.single().metadata["client_stage"])
        assertEquals("true", events.single().metadata["is_android"])
        assertEquals("true", events.single().metadata["has_fragment"])
        assertEquals("manual_button", events.single().metadata["launch_mode"])
    }
}
