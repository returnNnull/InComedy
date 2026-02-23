package com.bam.incomedy.feature.auth

import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.auth.providers.GoogleAuthProvider
import com.bam.incomedy.feature.auth.providers.TelegramAuthProvider
import com.bam.incomedy.feature.auth.providers.VkAuthProvider

object AuthModule {
    fun createViewModel(): AuthViewModel {
        val service = SocialAuthService(
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
        return AuthViewModel(service)
    }
}
