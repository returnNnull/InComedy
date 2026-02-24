package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.SessionTerminationService

class BackendSessionTerminationService(
    private val telegramBackendApi: TelegramBackendApi,
) : SessionTerminationService {
    override suspend fun terminate(accessToken: String): Result<Unit> {
        return telegramBackendApi.logout(accessToken)
    }
}
