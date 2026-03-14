package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.backend.TelegramBackendApi
import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider

class VkAuthProvider(
    private val backendApi: TelegramBackendApi,
) : SocialAuthProvider {
    override val type: AuthProviderType = AuthProviderType.VK

    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        return backendApi.startVkAuth()
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (code.isBlank()) return Result.failure(IllegalArgumentException("VK auth callback is empty"))
        return backendApi.verifyVk(code)
    }
}
