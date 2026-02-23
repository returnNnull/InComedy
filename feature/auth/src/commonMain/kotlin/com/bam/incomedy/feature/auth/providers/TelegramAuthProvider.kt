package com.bam.incomedy.feature.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider

class TelegramAuthProvider(
    private val botId: String,
    private val redirectUri: String,
) : SocialAuthProvider {
    override val type: AuthProviderType = AuthProviderType.TELEGRAM

    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        val url = buildUrl(
            base = "https://oauth.telegram.org/auth",
            params = mapOf(
                "bot_id" to botId,
                "origin" to redirectUri,
                "request_access" to "write",
                "state" to state,
            ),
        )
        return Result.success(AuthLaunchRequest(type, state, url))
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (code.isBlank()) return Result.failure(IllegalArgumentException("Telegram auth code is empty"))
        return Result.success(
            AuthSession(
                provider = type,
                userId = "tg_user_$state",
                accessToken = "tg_token_$code",
            ),
        )
    }
}
