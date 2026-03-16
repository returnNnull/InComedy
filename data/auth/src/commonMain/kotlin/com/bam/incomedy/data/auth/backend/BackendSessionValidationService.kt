package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.core.backend.BackendStatusException
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthorizedUser
import com.bam.incomedy.domain.auth.SessionValidationException
import com.bam.incomedy.domain.auth.SessionValidationFailureReason
import com.bam.incomedy.domain.auth.SessionValidationService
import com.bam.incomedy.domain.auth.ValidatedSession

/**
 * Реализация `SessionValidationService`, которая валидирует access token на backend
 * и при необходимости выполняет refresh.
 *
 * @property authBackendApi HTTP-клиент backend API auth/session lifecycle.
 */
class BackendSessionValidationService(
    private val authBackendApi: AuthBackendApi,
) : SessionValidationService {

    /** Проверяет access token и при необходимости обновляет сессию по refresh token. */
    override suspend fun validate(accessToken: String, refreshToken: String?): Result<ValidatedSession> {
        val directValidation = authBackendApi.getSessionUser(accessToken)
        if (directValidation.isSuccess) {
            return directValidation.map { user ->
                ValidatedSession(
                    provider = user.provider,
                    userId = user.id,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    user = AuthorizedUser(
                        id = user.id,
                        displayName = user.displayName,
                        username = user.username,
                        photoUrl = user.photoUrl,
                        roles = user.roles,
                        activeRole = user.activeRole,
                        linkedProviders = user.linkedProviders,
                    ),
                )
            }
        }

        val validationError = directValidation.exceptionOrNull()
            ?: IllegalStateException("Session validation failed")
        val validationReason = classifyFailure(validationError)

        if (validationReason == SessionValidationFailureReason.UNAUTHORIZED && !refreshToken.isNullOrBlank()) {
            return authBackendApi.refreshSession(refreshToken)
                .map { refreshed ->
                    ValidatedSession(
                        provider = refreshed.provider,
                        userId = refreshed.userId,
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        user = refreshed.user,
                    )
                }
                .recoverCatching { refreshError ->
                    throw SessionValidationException(
                        reason = classifyFailure(refreshError),
                        message = refreshError.message ?: "Session refresh failed",
                    )
                }
        }

        return Result.failure(
            SessionValidationException(
                reason = validationReason,
                message = validationError.message ?: "Session validation failed",
            ),
        )
    }

    /** Классифицирует backend-ошибку по типу отказа валидации сессии. */
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
