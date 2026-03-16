package com.bam.incomedy.domain.auth

/**
 * Порт first-party credential auth flow.
 *
 * Контракт описывает только бизнес-операции login/register и не фиксирует,
 * откуда именно берется сессия: backend, stub или другой адаптер.
 */
interface CredentialAuthService {
    /** Выполняет вход по логину и паролю. */
    suspend fun signIn(login: String, password: String): Result<AuthSession>

    /** Выполняет регистрацию по логину и паролю. */
    suspend fun register(login: String, password: String): Result<AuthSession>
}
