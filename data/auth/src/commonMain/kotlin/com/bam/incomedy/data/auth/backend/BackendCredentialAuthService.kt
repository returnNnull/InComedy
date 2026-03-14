package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.CredentialAuthService

class BackendCredentialAuthService(
    private val backendApi: TelegramBackendApi,
) : CredentialAuthService {
    override suspend fun signIn(login: String, password: String): Result<AuthSession> {
        return backendApi.signInWithPassword(
            login = login,
            password = password,
        )
    }

    override suspend fun register(login: String, password: String): Result<AuthSession> {
        return backendApi.registerWithPassword(
            login = login,
            password = password,
        )
    }
}
