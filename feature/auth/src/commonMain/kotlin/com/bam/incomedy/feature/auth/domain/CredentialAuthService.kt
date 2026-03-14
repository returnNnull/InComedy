package com.bam.incomedy.feature.auth.domain

interface CredentialAuthService {
    suspend fun signIn(login: String, password: String): Result<AuthSession>

    suspend fun register(login: String, password: String): Result<AuthSession>
}
