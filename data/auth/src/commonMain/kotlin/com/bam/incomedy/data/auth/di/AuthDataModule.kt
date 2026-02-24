package com.bam.incomedy.data.auth.di

import com.bam.incomedy.data.auth.backend.BackendSessionValidationService
import com.bam.incomedy.data.auth.backend.TelegramBackendApi
import com.bam.incomedy.data.auth.providers.GoogleAuthProvider
import com.bam.incomedy.data.auth.providers.TelegramAuthProvider
import com.bam.incomedy.data.auth.providers.VkAuthProvider
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    single {
        TelegramBackendApi()
    }
    single<SessionValidationService> {
        BackendSessionValidationService(telegramBackendApi = get())
    }

    single {
        VkAuthProvider(
            clientId = "VK_CLIENT_ID",
            redirectUri = "incomedy://auth/vk",
        )
    } bind SocialAuthProvider::class

    single {
        TelegramAuthProvider(
            botId = "8649746631",
            origin = "https://incomedy.ru",
            redirectUri = "https://incomedy.ru/auth/telegram/callback",
            backendApi = get(),
        )
    } bind SocialAuthProvider::class

    single {
        GoogleAuthProvider(
            clientId = "GOOGLE_CLIENT_ID",
            redirectUri = "incomedy://auth/google",
        )
    } bind SocialAuthProvider::class
}
