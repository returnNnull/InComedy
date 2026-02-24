package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

sealed interface AuthEffect {
    data class OpenExternalAuth(
        val provider: AuthProviderType,
        val url: String,
    ) : AuthEffect
    object InvalidateStoredSession : AuthEffect
}
