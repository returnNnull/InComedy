package com.bam.incomedy.domain.auth

/**
 * Порт завершения текущей авторизованной сессии.
 */
interface SessionTerminationService {
    /** Завершает сессию по access token. */
    suspend fun terminate(accessToken: String): Result<Unit>
}
