package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.domain.auth.SessionTerminationService

/**
 * Адаптер завершения пользовательской сессии к backend logout endpoint.
 *
 * @property authBackendApi Auth backend transport для lifecycle операций сессии.
 */
class BackendSessionTerminationService(
    private val authBackendApi: AuthBackendApi,
) : SessionTerminationService {
    /** Завершает текущую backend-сессию по access token. */
    override suspend fun terminate(accessToken: String): Result<Unit> {
        return authBackendApi.logout(accessToken)
    }
}
