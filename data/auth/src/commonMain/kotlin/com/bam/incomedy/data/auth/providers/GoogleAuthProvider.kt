package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider

class GoogleAuthProvider(
    private val clientId: String,
    private val redirectUri: String,
    private val scope: String = "openid email profile",
) : SocialAuthProvider {
    override val type: AuthProviderType = AuthProviderType.GOOGLE

    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        val url = buildUrl(
            base = "https://accounts.google.com/o/oauth2/v2/auth",
            params = mapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "scope" to scope,
                "access_type" to "offline",
                "include_granted_scopes" to "true",
                "state" to state,
            ),
        )
        return Result.success(AuthLaunchRequest(type, state, url))
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        if (code.isBlank()) return Result.failure(IllegalArgumentException("Google auth code is empty"))
        return Result.success(
            AuthSession(
                provider = type,
                userId = "google_user_$state",
                accessToken = "google_token_$code",
            ),
        )
    }
}

