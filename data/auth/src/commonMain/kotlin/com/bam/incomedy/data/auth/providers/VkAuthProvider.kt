package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider

class VkAuthProvider(
    private val clientId: String,
    private val redirectUri: String,
    private val scope: String = "email",
) : SocialAuthProvider {
    override val type: AuthProviderType = AuthProviderType.VK

    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        val url = buildUrl(
            base = "https://id.vk.com/authorize",
            params = mapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
                "state" to state,
            ),
        )
        return Result.success(AuthLaunchRequest(type, state, url))
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (code.isBlank()) return Result.failure(IllegalArgumentException("VK auth code is empty"))
        return Result.success(
            AuthSession(
                provider = type,
                userId = "vk_user_$state",
                accessToken = "vk_token_$code",
            ),
        )
    }
}

