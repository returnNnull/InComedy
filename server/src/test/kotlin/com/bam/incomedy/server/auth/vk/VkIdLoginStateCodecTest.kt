package com.bam.incomedy.server.auth.vk

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Тесты подписанного `state` для VK ID auth flow.
 */
class VkIdLoginStateCodecTest {

    /** Проверяет, что выданный `state` успешно проходит верификацию без потери PKCE-данных. */
    @Test
    fun `issued state round trips through verification`() {
        val now = Instant.parse("2026-03-14T20:00:00Z")
        val codec = testCodec(nowProvider = { now })

        val issuedState = codec.issue()
        val verifiedState = codec.verify(issuedState.state).getOrThrow()

        assertEquals(issuedState.codeVerifier, verifiedState.codeVerifier)
        assertEquals("https://incomedy.ru/auth/vk/callback", verifiedState.redirectUri)
        assertEquals(now.plusSeconds(600L), verifiedState.expiresAt)
        assertTrue(issuedState.codeChallenge.isNotBlank())
        assertTrue(issuedState.state.contains('.'))
    }

    /** Проверяет, что изменение подписи делает `state` недействительным. */
    @Test
    fun `verify rejects tampered signature`() {
        val codec = testCodec(nowProvider = { Instant.parse("2026-03-14T20:00:00Z") })
        val issuedState = codec.issue()
        val delimiterIndex = issuedState.state.lastIndexOf('.')
        val tamperedState = issuedState.state.substring(0, delimiterIndex + 1) + "tampered-signature"

        val failure = codec.verify(tamperedState).exceptionOrNull()

        assertIs<InvalidVkIdAuthStateException>(failure)
        assertEquals("VK ID auth state signature is invalid", failure.message)
    }

    /** Проверяет, что просроченный `state` больше не принимается для обмена кода. */
    @Test
    fun `verify rejects expired state`() {
        var now = Instant.parse("2026-03-14T20:00:00Z")
        val codec = testCodec(nowProvider = { now })
        val issuedState = codec.issue()
        now = now.plusSeconds(601L)

        val failure = codec.verify(issuedState.state).exceptionOrNull()

        assertIs<InvalidVkIdAuthStateException>(failure)
        assertEquals("VK ID auth state is expired", failure.message)
    }

    /** Строит codec с фиксированным redirect URI и секретом для предсказуемых тестов. */
    private fun testCodec(nowProvider: () -> Instant): VkIdLoginStateCodec {
        return VkIdLoginStateCodec(
            redirectUri = "https://incomedy.ru/auth/vk/callback",
            secret = "state-secret",
            ttlSeconds = 600L,
            nowProvider = nowProvider,
        )
    }
}
