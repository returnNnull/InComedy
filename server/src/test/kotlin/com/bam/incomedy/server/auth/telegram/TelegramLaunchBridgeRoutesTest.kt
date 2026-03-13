package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.observability.DiagnosticsQuery
import com.bam.incomedy.server.observability.InMemoryDiagnosticsStore
import com.bam.incomedy.server.security.InMemoryAuthRateLimiter
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests the Telegram first-party launch bridge page and safe telemetry capture. */
class TelegramLaunchBridgeRoutesTest {

    /** Launch bridge should render a first-party redirect page and record safe diagnostics. */
    @Test
    fun `launch bridge returns html and records diagnostics`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)
        val authService = testAuthService()
        val launch = authService.createLaunchRequest().getOrThrow()

        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                TelegramLaunchBridgeRoutes.register(
                    route = this,
                    authService = authService,
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/auth/telegram/launch?state=${launch.state}")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Continue to Telegram"))
        assertTrue(body.contains("window.location.replace(authUrl)"))
        assertTrue(body.contains("history.replaceState"))
        assertTrue(body.contains("/auth/telegram/launch/telemetry"))

        val events = diagnosticsStore.query(
            DiagnosticsQuery(
                routePrefix = "/auth/telegram/launch",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.launch.bridge.ready", events.single().stage)
        assertEquals("true", events.single().metadata["state_present"])
        assertEquals("true", events.single().metadata["launch_ready"])
    }

    /** Launch bridge should return a safe bad-request page when state is invalid or expired. */
    @Test
    fun `launch bridge rejects invalid state`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)

        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                TelegramLaunchBridgeRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get("/auth/telegram/launch?state=invalid_state")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Telegram sign-in link is invalid or expired."))

        val events = diagnosticsStore.query(
            DiagnosticsQuery(
                routePrefix = "/auth/telegram/launch",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.launch.bridge.invalid_state", events.single().stage)
        assertEquals("telegram_auth_state_invalid", events.single().safeErrorCode)
        assertEquals("false", events.single().metadata["launch_ready"])
    }

    /** Launch bridge telemetry endpoint should record safe client-side events. */
    @Test
    fun `launch bridge telemetry records client stage`() = testApplication {
        val diagnosticsStore = InMemoryDiagnosticsStore(retentionLimit = 10)

        environment {
            config = MapApplicationConfig()
        }
        application {
            routing {
                TelegramLaunchBridgeRoutes.register(
                    route = this,
                    authService = testAuthService(),
                    diagnosticsStore = diagnosticsStore,
                    rateLimiter = InMemoryAuthRateLimiter(),
                )
            }
        }

        val response = client.get(
            "/auth/telegram/launch/telemetry" +
                "?stage=redirect_attempted&is_android=true&has_auth_url=true&redirect_mode=auto_redirect",
        )

        assertEquals(HttpStatusCode.NoContent, response.status)
        val events = diagnosticsStore.query(
            DiagnosticsQuery(
                routePrefix = "/auth/telegram/launch/telemetry",
                limit = 10,
            ),
        )
        assertEquals(1, events.size)
        assertEquals("auth.telegram.launch.bridge.client_event", events.single().stage)
        assertEquals("redirect_attempted", events.single().metadata["client_stage"])
        assertEquals("true", events.single().metadata["is_android"])
        assertEquals("auto_redirect", events.single().metadata["redirect_mode"])
    }

    /** Creates a Telegram auth service with fixed launch and callback URIs for bridge tests. */
    private fun testAuthService(): TelegramAuthService {
        return TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = REDIRECT_URI,
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { NOW },
            ),
            oidcClient = FakeTelegramLaunchBridgeOidcGateway(),
            repository = InMemoryTelegramUserRepository(),
            tokenService = JwtSessionTokenService(
                JwtConfig(
                    issuer = "test",
                    secret = "0123456789abcdef0123456789abcdef",
                    accessTtlSeconds = 3600L,
                    refreshTtlSeconds = 86400L,
                ),
            ),
            launchUri = LAUNCH_URI,
        )
    }

    private companion object {
        /** Fixed public callback URI for first-party launch bridge tests. */
        const val REDIRECT_URI = "https://incomedy.ru/auth/telegram/callback"

        /** Fixed public first-party launch URI for bridge tests. */
        const val LAUNCH_URI = "https://incomedy.ru/auth/telegram/launch"

        /** Fixed current time for deterministic signed-state validation. */
        val NOW: Instant = Instant.parse("2036-03-13T08:00:00Z")
    }
}

/** Fake OIDC gateway used by launch bridge route tests. */
private class FakeTelegramLaunchBridgeOidcGateway : TelegramOidcGateway {
    /** Rebuilds a predictable official Telegram auth URL from signed state. */
    override fun buildAuthorizationUrl(state: String, codeVerifier: String): String {
        return "https://oauth.telegram.org/auth" +
            "?client_id=test-client-id" +
            "&redirect_uri=https%3A%2F%2Fincomedy.ru%2Fauth%2Ftelegram%2Fcallback" +
            "&response_type=code" +
            "&scope=openid%20profile%20phone" +
            "&state=$state" +
            "&code_challenge=test_challenge" +
            "&code_challenge_method=S256"
    }

    /** Verify is irrelevant for the launch bridge route tests. */
    override fun exchangeAndVerify(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<VerifiedTelegramAuth> {
        error("exchangeAndVerify should not be called by launch bridge tests")
    }
}
