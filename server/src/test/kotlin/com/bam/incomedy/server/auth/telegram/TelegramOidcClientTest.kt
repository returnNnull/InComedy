package com.bam.incomedy.server.auth.telegram

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit-тесты реального Telegram OIDC client-а для параметров authorization URL.
 */
class TelegramOidcClientTest {

    /** Authorization URL должен запрашивать официальный набор scope вместе с PKCE-параметрами. */
    @Test
    fun `buildAuthorizationUrl includes documented scope and pkce parameters`() {
        val client = TelegramOidcClient(
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            redirectUri = "https://incomedy.ru/auth/telegram/callback",
        )

        val url = client.buildAuthorizationUrl(
            state = "signed_state",
            codeVerifier = "pkce-verifier-0123456789",
        )

        assertTrue(url.startsWith("https://oauth.telegram.org/auth?"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fincomedy.ru%2Fauth%2Ftelegram%2Fcallback"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("scope=openid%20profile%20phone"))
        assertTrue(url.contains("state=signed_state"))
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
    }
}
