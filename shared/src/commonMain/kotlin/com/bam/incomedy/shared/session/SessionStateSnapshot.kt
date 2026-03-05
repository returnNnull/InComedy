package com.bam.incomedy.shared.session

data class SessionStateSnapshot(
    val isAuthorized: Boolean,
    val providerKey: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?,
    val displayName: String?,
    val username: String?,
    val photoUrl: String?,
)
