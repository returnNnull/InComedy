package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.backend.AuthBackendApi
import com.bam.incomedy.domain.auth.AuthLaunchRequest
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.SocialAuthProvider

/**
 * Провайдер VK auth flow поверх backend-issued launch/verify endpoints.
 *
 * Провайдер не хранит transport детали VK SDK/browser callback и использует
 * `AuthBackendApi` как единый шлюз между client-side входом и внутренней сессией.
 *
 * @property backendApi Auth backend transport для VK start/verify операций.
 */
class VkAuthProvider(
    private val backendApi: AuthBackendApi,
) : SocialAuthProvider {
    override val type: AuthProviderType = AuthProviderType.VK

    /** Запрашивает launch request для старта VK auth flow. */
    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        return backendApi.startVkAuth()
    }

    /** Передает backend-у успешный callback после VK auth. */
    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (code.isBlank()) return Result.failure(IllegalArgumentException("VK auth callback is empty"))
        return backendApi.verifyVk(code)
    }
}
