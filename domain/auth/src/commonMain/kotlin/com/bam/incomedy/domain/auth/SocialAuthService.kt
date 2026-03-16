package com.bam.incomedy.domain.auth

/**
 * Координатор внешних auth-провайдеров.
 *
 * Сервис выбирает реализацию по `AuthProviderType` и дает feature-слою один
 * унифицированный вход в launch/complete операции.
 *
 * @property providersByType Карта доступных provider adapter-ов по их типу.
 */
class SocialAuthService(
    providers: List<SocialAuthProvider>,
) {
    private val providersByType = providers.associateBy { it.type }

    /** Запрашивает launch request для выбранного внешнего провайдера. */
    suspend fun requestLaunch(provider: AuthProviderType, state: String): Result<AuthLaunchRequest> {
        val authProvider = providersByType[provider]
            ?: return Result.failure(IllegalArgumentException("Provider $provider is not configured"))
        return authProvider.createLaunchRequest(state)
    }

    /** Завершает callback flow у выбранного внешнего провайдера. */
    suspend fun complete(provider: AuthProviderType, code: String, state: String): Result<AuthSession> {
        val authProvider = providersByType[provider]
            ?: return Result.failure(IllegalArgumentException("Provider $provider is not configured"))
        return authProvider.exchangeCode(code, state)
    }
}
