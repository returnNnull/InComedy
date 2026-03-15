package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.observability.DiagnosticsQuery
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

/** Tests the VK HTTPS callback bridge response and safe diagnostics capture. */
class VkIdCallbackBridgeRoutesTest {

    /** The callback bridge should serve fallback HTML and record a safe bridge-hit event. */
    @Test
    fun `callback bridge returns html and records diagnostics`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)
        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                VkIdCallbackBridgeRoutes.register(
                    route = this,
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/auth/vk/callback?code=vk_code&state=test_state&device_id=device123")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Returning to InComedy app..."))
        assertTrue(body.contains("Open app"))
        assertTrue(body.contains("incomedy://auth/vk?"))
        assertTrue(body.contains("/auth/vk/callback/telemetry"))
        assertTrue(body.contains("history.replaceState"))
        assertTrue(body.contains("autolaunch_attempted"))

        val events = diagnosticsStore.query(
            DiagnosticsQuery(
                routePrefix = "/auth/vk/callback",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.vk.callback.bridge.hit", events.single().stage)
        assertEquals("true", events.single().metadata["has_code"])
        assertEquals("true", events.single().metadata["has_device_id"])
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
                VkIdCallbackBridgeRoutes.register(
                    route = this,
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get(
            "/auth/vk/callback/telemetry" +
                "?stage=open_app_clicked&is_android=true&has_query=true&has_fragment=false&has_payload=true&launch_mode=manual_button",
        )

        assertEquals(HttpStatusCode.NoContent, response.status)
        val events = diagnosticsStore.query(
            DiagnosticsQuery(
                routePrefix = "/auth/vk/callback/telemetry",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.vk.callback.bridge.client_event", events.single().stage)
        assertEquals("open_app_clicked", events.single().metadata["client_stage"])
        assertEquals("true", events.single().metadata["is_android"])
        assertEquals("true", events.single().metadata["has_query"])
        assertEquals("manual_button", events.single().metadata["launch_mode"])
    }
}
