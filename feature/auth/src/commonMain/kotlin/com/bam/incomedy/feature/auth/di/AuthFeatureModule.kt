package com.bam.incomedy.feature.auth.di

import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.auth.providers.GoogleAuthProvider
import com.bam.incomedy.feature.auth.providers.TelegramAuthProvider
import com.bam.incomedy.feature.auth.providers.VkAuthProvider
import org.koin.dsl.module

val authFeatureModule = module {
    single {
        SocialAuthService(
            providers = listOf(
                VkAuthProvider(
                    clientId = "VK_CLIENT_ID",
                    redirectUri = "incomedy://auth/vk",
                ),
                TelegramAuthProvider(
                    botId = "TELEGRAM_BOT_ID",
                    redirectUri = "incomedy://auth/telegram",
                ),
                GoogleAuthProvider(
                    clientId = "GOOGLE_CLIENT_ID",
                    redirectUri = "incomedy://auth/google",
                ),
            ),
        )
    }

    factory {
        AuthViewModel(socialAuthService = get())
    }
}
