package com.bam.incomedy.feature.auth.domain

data class AuthLaunchRequest(
    val provider: AuthProviderType,
    val state: String,
    val url: String,
)

data class AuthSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val user: AuthorizedUser,
)

data class AuthorizedUser(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val photoUrl: String? = null,
)
