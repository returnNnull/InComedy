package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.SessionValidationException
import com.bam.incomedy.feature.auth.domain.SessionValidationFailureReason
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.ValidatedSession

class BackendSessionValidationService(
    private val telegramBackendApi: TelegramBackendApi,
) : SessionValidationService {
    override suspend fun validate(accessToken: String): Result<ValidatedSession> {
        return telegramBackendApi.getSessionUser(accessToken)
            .map { user ->
                ValidatedSession(
                    provider = AuthProviderType.TELEGRAM,
                    userId = user.id,
                    accessToken = accessToken,
                )
            }
            .recoverCatching { error ->
                throw SessionValidationException(
                    reason = classifyFailure(error),
                    message = error.message ?: "Session validation failed",
                )
            }
    }

    private fun classifyFailure(error: Throwable): SessionValidationFailureReason {
        if (error is BackendStatusException && error.statusCode == 401) {
            return SessionValidationFailureReason.UNAUTHORIZED
        }
        val message = error.message?.lowercase().orEmpty()
        return when {
            "resolve host" in message || "timeout" in message || "connection" in message -> {
                SessionValidationFailureReason.NETWORK
            }
            else -> SessionValidationFailureReason.UNKNOWN
        }
    }
}
