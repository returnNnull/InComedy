package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
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
                )
            }
        }

        val response = client.get("/auth/telegram/callback?id=42&auth_date=1700000000&hash=test_hash&state=test_state")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("intent://auth/telegram?"))
        assertTrue(body.contains("history.replaceState"))
        assertTrue(body.contains("Tap Open app to finish signing in."))

        val events = diagnosticsStore.query(
            com.bam.incomedy.server.observability.DiagnosticsQuery(
                routePrefix = "/auth/telegram/callback",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.callback.bridge.hit", events.single().stage)
        assertEquals("true", events.single().metadata["has_id"])
        assertEquals("true", events.single().metadata["has_auth_date"])
        assertEquals("true", events.single().metadata["has_hash"])
        assertEquals("true", events.single().metadata["state_present"])
    }
}
