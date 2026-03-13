package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.backend.TelegramAuthGateway
import com.bam.incomedy.data.auth.backend.TelegramAuthLaunch
import com.bam.incomedy.data.auth.backend.TelegramBackendSession
import com.bam.incomedy.data.auth.backend.TelegramVerifyPayload
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthorizedUser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты Telegram provider-а, работающего через backend-driven launch/verify flow.
 */
class TelegramAuthProviderLaunchUrlTest {

    /** Provider должен возвращать auth URL и state, полученные от backend-а. */
    @Test
    fun `telegram auth launch request uses backend launch response`() {
        val provider = TelegramAuthProvider(
            gateway = FakeTelegramAuthGateway(
                launch = TelegramAuthLaunch(
                    authUrl = "https://incomedy.ru/auth/telegram/launch?state=server_state",
                    state = "server_state",
                ),
            ),
        )

        val request = runBlocking {
            provider.createLaunchRequest(state = "ignored_client_state").getOrThrow()
        }

        assertEquals(AuthProviderType.TELEGRAM, request.provider)
        assertEquals("server_state", request.state)
        assertTrue(request.url.contains("https://incomedy.ru/auth/telegram/launch"))
        assertTrue(request.url.contains("state=server_state"))
    }

    /** Provider должен извлекать `code/state` из callback URL и передавать их backend-у. */
    @Test
    fun `telegram auth exchange forwards code and state from callback`() {
        val gateway = FakeTelegramAuthGateway(
            launch = TelegramAuthLaunch(
                authUrl = "https://incomedy.ru/auth/telegram/launch?state=server_state",
                state = "server_state",
            ),
        )
        val provider = TelegramAuthProvider(gateway = gateway)

        val session = runBlocking {
            provider.exchangeCode(
                code = "incomedy://auth/telegram?code=oidc_code&state=server_state",
                state = "server_state",
            ).getOrThrow()
        }

        assertEquals("oidc_code", gateway.lastVerifyPayload?.code)
        assertEquals("server_state", gateway.lastVerifyPayload?.state)
        assertEquals("user-1", session.userId)
    }
}

/**
 * Простой fake gateway для unit-тестов Telegram auth provider-а.
 *
 * @property launch Статический launch-ответ backend-а.
 */
private class FakeTelegramAuthGateway(
    private val launch: TelegramAuthLaunch,
) : TelegramAuthGateway {
    /** Последний verify payload, переданный provider-ом в fake backend. */
    var lastVerifyPayload: TelegramVerifyPayload? = null
        private set

    /** Возвращает заранее заданный launch-ответ. */
    override suspend fun startTelegramAuth(): Result<TelegramAuthLaunch> {
        return Result.success(launch)
    }

    /** Сохраняет verify payload и возвращает фиктивную backend-сессию. */
    override suspend fun verifyTelegram(payload: TelegramVerifyPayload): Result<TelegramBackendSession> {
        lastVerifyPayload = payload
        return Result.success(
            TelegramBackendSession(
                userId = "user-1",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                user = AuthorizedUser(
                    id = "user-1",
                    displayName = "Telegram User",
                    username = "telegram_user",
                    photoUrl = null,
                ),
            ),
        )
    }
}
