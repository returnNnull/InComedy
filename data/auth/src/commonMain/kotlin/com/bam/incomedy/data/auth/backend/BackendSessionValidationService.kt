package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.SessionValidationService
import com.bam.incomedy.feature.auth.domain.ValidatedSession

class BackendSessionValidationService(
    private val telegramBackendApi: TelegramBackendApi,
) : SessionValidationService {
    override suspend fun validate(accessToken: String): Result<ValidatedSession> {
        return telegramBackendApi.getSessionUser(accessToken).map { user ->
            ValidatedSession(
                provider = AuthProviderType.TELEGRAM,
                userId = user.id,
                accessToken = accessToken,
            )
        }
    }
}
