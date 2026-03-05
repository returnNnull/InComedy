package com.bam.incomedy.feature.auth.domain

interface SessionValidationService {
    suspend fun validate(accessToken: String, refreshToken: String? = null): Result<ValidatedSession>
}

data class ValidatedSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val user: AuthorizedUser,
)

enum class SessionValidationFailureReason {
    UNAUTHORIZED,
    NETWORK,
    UNKNOWN,
}

class SessionValidationException(
    val reason: SessionValidationFailureReason,
    message: String,
) : Exception(message)
