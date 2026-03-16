package com.bam.incomedy.domain.auth

/**
 * Порт конкретного внешнего auth-провайдера.
 *
 * Реализации прячут provider-specific launch/exchange transport и выдают
 * общий доменный контракт для orchestration слоя.
 */
interface SocialAuthProvider {
    /** Тип провайдера, за который отвечает реализация. */
    val type: AuthProviderType

    /** Готовит запуск внешнего auth flow. */
    suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest>

    /** Завершает auth flow после callback-а и возвращает внутреннюю сессию. */
    suspend fun exchangeCode(code: String, state: String): Result<AuthSession>
}
