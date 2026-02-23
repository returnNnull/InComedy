package com.bam.incomedy.feature.auth.domain

class SocialAuthService(
    providers: List<SocialAuthProvider>,
) {
    private val providersByType = providers.associateBy { it.type }

    suspend fun requestLaunch(provider: AuthProviderType, state: String): Result<AuthLaunchRequest> {
        val authProvider = providersByType[provider]
            ?: return Result.failure(IllegalArgumentException("Provider $provider is not configured"))
        return authProvider.createLaunchRequest(state)
    }

    suspend fun complete(provider: AuthProviderType, code: String, state: String): Result<AuthSession> {
        val authProvider = providersByType[provider]
            ?: return Result.failure(IllegalArgumentException("Provider $provider is not configured"))
        return authProvider.exchangeCode(code, state)
    }
}
