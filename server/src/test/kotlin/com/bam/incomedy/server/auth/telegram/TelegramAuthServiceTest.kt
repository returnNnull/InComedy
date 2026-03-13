package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit-тесты Telegram auth orchestration слоя без реальных сетевых вызовов Telegram.
 */
class TelegramAuthServiceTest {

    /** Повторное использование одного и того же успешного auth утверждения должно отклоняться. */
    @Test
    fun `verifyAndCreateSession rejects replayed telegram assertion`() {
        val repository = InMemoryTelegramUserRepository()
        val service = TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = REDIRECT_URI,
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { NOW },
            ),
            oidcClient = FakeTelegramServiceOidcGateway(),
            repository = repository,
            tokenService = testTokenService(),
        )
        val launch = service.createLaunchRequest().getOrThrow()
        val request = TelegramVerifyRequest(
            code = "oidc_code",
            state = launch.state,
        )

        val firstResult = service.verifyAndCreateSession(request)
        val secondResult = service.verifyAndCreateSession(request)

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isFailure)
        assertIs<ReplayedTelegramAuthException>(secondResult.exceptionOrNull())
    }

    /** Серверный auth start должен возвращать официальный launch URL и state. */
    @Test
    fun `createLaunchRequest returns official auth url and state`() {
        val service = TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = REDIRECT_URI,
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { NOW },
            ),
            oidcClient = FakeTelegramServiceOidcGateway(),
            repository = InMemoryTelegramUserRepository(),
            tokenService = testTokenService(),
        )

        val launch = service.createLaunchRequest().getOrThrow()

        assertTrue(launch.authUrl.contains("https://oauth.telegram.org/auth"))
        assertTrue(launch.authUrl.contains("response_type=code"))
        assertTrue(launch.authUrl.contains("client_id=test-client-id"))
        assertTrue(launch.state.isNotBlank())
    }

    /** Успешный verify flow должен создавать внутреннюю сессию для Telegram user id из OIDC. */
    @Test
    fun `verifyAndCreateSession issues internal session for oidc user`() {
        val service = TelegramAuthService(
            loginStateCodec = TelegramLoginStateCodec(
                redirectUri = REDIRECT_URI,
                secret = "state-secret",
                ttlSeconds = 600L,
                nowProvider = { NOW },
            ),
            oidcClient = FakeTelegramServiceOidcGateway(),
            repository = InMemoryTelegramUserRepository(),
            tokenService = testTokenService(),
        )
        val launch = service.createLaunchRequest().getOrThrow()

        val result = service.verifyAndCreateSession(
            TelegramVerifyRequest(
                code = "oidc_code",
                state = launch.state,
            ),
        ).getOrThrow()

        assertTrue(result.accessToken.isNotBlank())
        assertTrue(result.refreshToken.isNotBlank())
        assertEquals("telegram_user", result.user.username)
    }

    /** Строит стандартный JWT token service для Telegram auth unit-тестов. */
    private fun testTokenService(): JwtSessionTokenService {
        return JwtSessionTokenService(
            JwtConfig(
                issuer = "test",
                secret = "0123456789abcdef0123456789abcdef",
                accessTtlSeconds = 3600L,
                refreshTtlSeconds = 86400L,
            ),
        )
    }

    private companion object {
        /** Фиксированный redirect URI для server-side Telegram auth тестов. */
        const val REDIRECT_URI = "https://incomedy.ru/auth/telegram/callback"

        /** Фиксированное текущее время для предсказуемой проверки state TTL. */
        val NOW: Instant = Instant.parse("2036-03-13T08:00:00Z")
    }
}

/**
 * Fake Telegram OIDC gateway для unit-тестов Telegram auth orchestration.
 */
private class FakeTelegramServiceOidcGateway : TelegramOidcGateway {
    /** Формирует предсказуемый Telegram auth URL для тестов. */
    override fun buildAuthorizationUrl(state: String, codeVerifier: String): String {
        return "https://oauth.telegram.org/auth" +
            "?client_id=test-client-id" +
            "&redirect_uri=https%3A%2F%2Fincomedy.ru%2Fauth%2Ftelegram%2Fcallback" +
            "&response_type=code" +
            "&scope=openid%20profile" +
            "&state=$state" +
            "&code_challenge=test_challenge" +
            "&code_challenge_method=S256"
    }

    /** Возвращает фиксированный Telegram профиль как результат успешного OIDC verify. */
    override fun exchangeAndVerify(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<VerifiedTelegramAuth> {
        return Result.success(
            VerifiedTelegramAuth(
                user = TelegramUser(
                    id = 10001L,
                    firstName = "Telegram User",
                    lastName = null,
                    username = "telegram_user",
                    photoUrl = null,
                ),
                assertionHash = "assertion-$code",
                replayExpiresAt = state.expiresAt,
            ),
        )
    }
}
