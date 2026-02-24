package com.bam.incomedy.shared.auth

data class AuthUiStateSnapshot(
    val isLoading: Boolean,
    val selectedProviderKey: String?,
    val errorMessage: String?,
    val isAuthorized: Boolean,
    val authorizedProviderKey: String?,
    val authorizedUserId: String?,
    val authorizedAccessToken: String?,
)
