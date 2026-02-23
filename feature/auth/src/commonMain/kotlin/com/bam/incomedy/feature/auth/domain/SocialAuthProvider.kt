package com.bam.incomedy.feature.auth.domain

interface SocialAuthProvider {
    val type: AuthProviderType

    suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest>

    suspend fun exchangeCode(code: String, state: String): Result<AuthSession>
}
