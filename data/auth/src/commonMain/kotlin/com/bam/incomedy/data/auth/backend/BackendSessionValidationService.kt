package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthorizedUser
import com.bam.incomedy.feature.auth.domain.SessionValidationException
import com.bam.incomedy.feature.auth.domain.SessionValidationFailureReason
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.ValidatedSession

/**
 * Реализация `SessionValidationService`, которая валидирует access token на backend
 * и при необходимости выполняет refresh.
 *
 * @property telegramBackendApi HTTP-клиент backend API сессии.
 */
class BackendSessionValidationService(
    private val telegramBackendApi: TelegramBackendApi,
) : SessionValidationService {

    /** Проверяет access token и при необходимости обновляет сессию по refresh token. */
    override suspend fun validate(accessToken: String, refreshToken: String?): Result<ValidatedSession> {
        val directValidation = telegramBackendApi.getSessionUser(accessToken)
        if (directValidation.isSuccess) {
            return directValidation.map { user ->
                ValidatedSession(
                    provider = AuthProviderType.TELEGRAM,
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
            return telegramBackendApi.refreshSession(refreshToken)
                .map { refreshed ->
                    ValidatedSession(
                        provider = AuthProviderType.TELEGRAM,
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
