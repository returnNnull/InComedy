package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.di.authDataModule
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.core.context.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты Telegram launch URL конфигурации в data-layer DI.
 */
class TelegramAuthProviderLaunchUrlTest {

    /** Закрывает глобальный Koin-контекст после каждого теста. */
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    /** Telegram launch URL должен возвращать пользователя на HTTPS callback bridge домена. */
    @Test
    fun `telegram auth launch request uses domain callback bridge`() {
        val koin = startKoin {
            modules(authDataModule)
        }.koin
        val telegramProvider = koin.getAll<SocialAuthProvider>()
            .single { it.type == AuthProviderType.TELEGRAM }

        val request = runBlocking {
            telegramProvider.createLaunchRequest(state = "fixed_state").getOrThrow()
        }

        assertEquals(AuthProviderType.TELEGRAM, request.provider)
        assertTrue(request.url.contains("origin=https%3A%2F%2Fincomedy.ru"))
        assertTrue(request.url.contains("return_to=https%3A%2F%2Fincomedy.ru%2Fauth%2Ftelegram%2Fcallback"))
        assertTrue(!request.url.contains("return_to=incomedy%3A%2F%2Fauth%2Ftelegram"))
    }
}
