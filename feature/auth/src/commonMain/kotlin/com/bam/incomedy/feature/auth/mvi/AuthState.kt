package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession

data class AuthState(
    val isLoading: Boolean = false,
    val selectedProvider: AuthProviderType? = null,
    val errorMessage: String? = null,
    val session: AuthSession? = null,
) {
    val isAuthorized: Boolean = session != null
}
