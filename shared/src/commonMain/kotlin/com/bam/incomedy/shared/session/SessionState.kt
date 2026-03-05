package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthProviderType

data class SessionState(
    val isAuthorized: Boolean = false,
    val provider: AuthProviderType? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null,
)
