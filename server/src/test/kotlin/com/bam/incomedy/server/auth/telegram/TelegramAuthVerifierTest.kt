package com.bam.incomedy.server.auth.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramAuthVerifierTest {

    @Test
    fun `verify succeeds for valid payload`() {
        val verifier = TelegramAuthVerifier(botToken = "test_bot_token")
        val payload = TelegramVerifyRequest(
            id = 10001L,
            firstName = "InComedy",
            lastName = "Bot",
            username = "incomedy_bot",
            photoUrl = "https://t.me/i/userpic/320/incomedy_bot.jpg",
            authDate = 1_700_000_000L,
            hash = "will-be-replaced",
        )
        val hash = verifier.computeHash(payload)
        val result = verifier.verify(payload.copy(hash = hash), nowEpochSeconds = 1_700_000_100L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `verify fails for invalid hash`() {
        val verifier = TelegramAuthVerifier(botToken = "test_bot_token")
        val payload = TelegramVerifyRequest(
            id = 10001L,
            firstName = "InComedy",
            authDate = 1_700_000_000L,
            hash = "invalid",
        )
        val result = verifier.verify(payload, nowEpochSeconds = 1_700_000_100L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `verify fails for expired payload`() {
        val verifier = TelegramAuthVerifier(botToken = "test_bot_token", maxAuthAgeSeconds = 5L)
        val payload = TelegramVerifyRequest(
            id = 10001L,
            firstName = "InComedy",
            authDate = 1_700_000_000L,
            hash = "invalid",
        )
        val result = verifier.verify(payload, nowEpochSeconds = 1_700_000_010L)

        assertFalse(result.isSuccess)
    }
}

