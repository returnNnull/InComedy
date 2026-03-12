package com.bam.incomedy.feature.auth.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты безопасной callback summary для Android auth handoff logging.
 */
class AuthCallbackLogSummaryTest {

    /** Summary не должна включать сырые значения Telegram payload, но должна отражать форму callback-а. */
    @Test
    fun `summary hides raw telegram callback values`() {
        val summary = safeAuthCallbackSummary(
            "incomedy://auth/telegram?tgAuthResult=very-secret-payload&state=fixed_state&hash=raw_hash&id=123456",
        )

        assertTrue(summary.contains("providerHint=telegram"))
        assertTrue(summary.contains("hasTgAuthResult=true"))
        assertTrue(summary.contains("statePresent=true"))
        assertTrue(summary.contains("hasHash=true"))
        assertTrue(summary.contains("hasTelegramId=true"))
        assertFalse(summary.contains("very-secret-payload"))
        assertFalse(summary.contains("fixed_state"))
        assertFalse(summary.contains("raw_hash"))
        assertFalse(summary.contains("123456"))
    }

    /** Пустой callback должен логироваться как отсутствие URL, без лишних деталей. */
    @Test
    fun `summary marks missing callback url`() {
        val summary = safeAuthCallbackSummary(null)

        assertTrue(summary.contains("hasUrl=false"))
    }
}
