package com.bam.incomedy.domain.auth

/**
 * Порт проверки и восстановления существующей сессии.
 *
 * Контракт используется startup restore flow и инкапсулирует решение:
 * достаточно ли access token или нужен refresh fallback.
 */
interface SessionValidationService {
    /** Валидирует текущие токены и возвращает актуализированную сессию. */
    suspend fun validate(accessToken: String, refreshToken: String? = null): Result<ValidatedSession>
}

/**
 * Результат успешной проверки или обновления сессии.
 *
 * @property provider Провайдер, из которого восстановлена внутренняя сессия.
 * @property userId Внутренний идентификатор пользователя.
 * @property accessToken Актуальный access token.
 * @property refreshToken Актуальный refresh token, если он выдан.
 * @property user Актуальный профиль пользователя.
 */
data class ValidatedSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val user: AuthorizedUser,
)

/**
 * Нормализованные причины отказа при восстановлении сессии.
 */
enum class SessionValidationFailureReason {
    UNAUTHORIZED,
    NETWORK,
    UNKNOWN,
}

/**
 * Ошибка restore/refresh потока, уже сведенная к доменной причине.
 *
 * @property reason Нормализованная причина отказа валидации.
 */
class SessionValidationException(
    val reason: SessionValidationFailureReason,
    message: String,
) : Exception(message)
