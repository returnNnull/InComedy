package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession

sealed interface AuthIntent {
    data class OnSignInSubmit(
        val login: String,
        val password: String,
    ) : AuthIntent
    data class OnRegisterSubmit(
        val login: String,
        val password: String,
    ) : AuthIntent
    data class OnProviderClick(val provider: AuthProviderType) : AuthIntent
    data class OnAuthCallback(
        val provider: AuthProviderType,
        val code: String,
        val state: String,
    ) : AuthIntent
    data class OnAuthFailure(
        val provider: AuthProviderType,
        val message: String,
    ) : AuthIntent
    data class OnRestoreSessionTokens(
        val accessToken: String,
        val refreshToken: String? = null,
    ) : AuthIntent
    data class OnRestoreSessionToken(val accessToken: String) : AuthIntent
    data class OnRestoreSession(val session: AuthSession) : AuthIntent
    object OnSignOut : AuthIntent
    object OnClearError : AuthIntent
}
