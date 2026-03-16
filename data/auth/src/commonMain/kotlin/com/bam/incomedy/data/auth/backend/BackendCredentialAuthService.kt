package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.CredentialAuthService

/**
 * Адаптер credential auth use-case к backend transport.
 *
 * Сервис удерживает только username/password сценарии и делегирует HTTP-детали
 * в `AuthBackendApi`, чтобы orchestration слой не знал о wire contract.
 *
 * @property backendApi Auth backend transport для credential login/register.
 */
class BackendCredentialAuthService(
    private val backendApi: AuthBackendApi,
) : CredentialAuthService {
    /** Выполняет login по логину и паролю. */
    override suspend fun signIn(login: String, password: String): Result<AuthSession> {
        return backendApi.signInWithPassword(
            login = login,
            password = password,
        )
    }

    /** Выполняет регистрацию и сразу возвращает созданную сессию. */
    override suspend fun register(login: String, password: String): Result<AuthSession> {
        return backendApi.registerWithPassword(
            login = login,
            password = password,
        )
    }
}
