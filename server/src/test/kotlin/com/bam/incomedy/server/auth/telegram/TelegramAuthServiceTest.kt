package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TelegramAuthServiceTest {

    @Test
    fun `verifyAndCreateSession rejects replayed telegram payload`() {
        val verifier = TelegramAuthVerifier(botToken = "test_bot_token", maxAuthAgeSeconds = 300L)
        val repository = InMemoryTelegramUserRepository()
        val tokenService = JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
        val service = TelegramAuthService(verifier, repository, tokenService)
        val request = TelegramVerifyRequest(
            id = 10001L,
            firstName = "InComedy",
            lastName = "Bot",
            username = "incomedy_bot",
            photoUrl = "https://t.me/i/userpic/320/incomedy_bot.jpg",
            authDate = Instant.now().epochSecond,
            hash = "placeholder",
        ).let { payload ->
            payload.copy(hash = verifier.computeHash(payload))
        }

        val firstResult = service.verifyAndCreateSession(request)
        val secondResult = service.verifyAndCreateSession(request)

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isFailure)
        assertIs<ReplayedTelegramAuthException>(secondResult.exceptionOrNull())
    }
}
