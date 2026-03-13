package com.bam.incomedy.data.auth.di

import com.bam.incomedy.data.auth.backend.BackendSessionContextService
import com.bam.incomedy.data.auth.backend.BackendSessionTerminationService
import com.bam.incomedy.data.auth.backend.BackendSessionValidationService
import com.bam.incomedy.data.auth.backend.TelegramAuthGateway
import com.bam.incomedy.data.auth.backend.TelegramBackendApi
import com.bam.incomedy.data.auth.providers.GoogleAuthProvider
import com.bam.incomedy.data.auth.providers.TelegramAuthProvider
import com.bam.incomedy.data.auth.providers.VkAuthProvider
import com.bam.incomedy.feature.auth.domain.SessionContextService
import com.bam.incomedy.feature.auth.domain.SessionTerminationService
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/** DI-модуль data-слоя авторизации и серверного session/profile API. */
val authDataModule = module {
    single {
        TelegramBackendApi()
    } bind TelegramAuthGateway::class
    single<SessionValidationService> {
        BackendSessionValidationService(telegramBackendApi = get())
    }
    single<SessionContextService> {
        BackendSessionContextService(telegramBackendApi = get())
    }
    single<SessionTerminationService> {
        BackendSessionTerminationService(telegramBackendApi = get())
    }

    single {
        VkAuthProvider(
            clientId = "VK_CLIENT_ID",
            redirectUri = "incomedy://auth/vk",
        )
    } bind SocialAuthProvider::class

    single {
        TelegramAuthProvider(
            gateway = get(),
        )
    } bind SocialAuthProvider::class

    single {
        GoogleAuthProvider(
            clientId = "GOOGLE_CLIENT_ID",
            redirectUri = "incomedy://auth/google",
        )
    } bind SocialAuthProvider::class
}
