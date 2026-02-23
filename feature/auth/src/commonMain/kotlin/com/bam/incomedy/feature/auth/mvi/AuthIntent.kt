package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

sealed interface AuthIntent {
    data class OnProviderClick(val provider: AuthProviderType) : AuthIntent
    data class OnAuthCallback(
        val provider: AuthProviderType,
        val code: String,
        val state: String,
    ) : AuthIntent
    object OnClearError : AuthIntent
}
