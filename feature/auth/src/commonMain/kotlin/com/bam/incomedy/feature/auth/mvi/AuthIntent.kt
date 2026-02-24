package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession

sealed interface AuthIntent {
    data class OnProviderClick(val provider: AuthProviderType) : AuthIntent
    data class OnAuthCallback(
        val provider: AuthProviderType,
        val code: String,
        val state: String,
    ) : AuthIntent
    data class OnRestoreSessionToken(val accessToken: String) : AuthIntent
    data class OnRestoreSession(val session: AuthSession) : AuthIntent
    object OnSignOut : AuthIntent
    object OnClearError : AuthIntent
}
