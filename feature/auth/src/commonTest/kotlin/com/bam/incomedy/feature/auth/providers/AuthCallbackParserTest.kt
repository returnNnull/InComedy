package com.bam.incomedy.feature.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты разбора callback URL для мобильного auth handoff.
 */
class AuthCallbackParserTest {

    /** Telegram deep link с OIDC `code/state` должен корректно определяться как callback Telegram. */
    @Test
    fun `parses telegram callback from oidc deep link query`() {
        val url = "incomedy://auth/telegram?code=oidc_code&state=test_state"

        val parsed = AuthCallbackParser.parse(url)

        assertNotNull(parsed)
        assertEquals(AuthProviderType.TELEGRAM, parsed.provider)
        assertEquals(url, parsed.code)
        assertEquals("test_state", parsed.state)
    }

    /** Telegram deep link с OIDC fragment payload тоже должен проходить в общий auth flow. */
    @Test
    fun `parses telegram callback from oidc deep link fragment`() {
        val url = "incomedy://auth/telegram#code=oidc_code&state=test_state"

        val parsed = AuthCallbackParser.parse(url)

        assertNotNull(parsed)
        assertEquals(AuthProviderType.TELEGRAM, parsed.provider)
        assertEquals(url, parsed.code)
        assertEquals("test_state", parsed.state)
    }
}
