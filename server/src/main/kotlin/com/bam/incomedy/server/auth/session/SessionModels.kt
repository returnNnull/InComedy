package com.bam.incomedy.server.auth.session

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

data class SessionUser(
    val id: String,
    val displayName: String,
    val username: String?,
    val photoUrl: String?,
)

