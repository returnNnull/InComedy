package com.bam.incomedy.feature.auth.domain

interface SessionValidationService {
    suspend fun validate(accessToken: String): Result<ValidatedSession>
}

data class ValidatedSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
)
