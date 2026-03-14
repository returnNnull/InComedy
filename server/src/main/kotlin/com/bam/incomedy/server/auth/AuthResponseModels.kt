package com.bam.incomedy.server.auth

import com.bam.incomedy.server.db.StoredUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class IssuedAuthSession(
    val provider: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: StoredUser,
)

@Serializable
data class AuthSuccessResponse(
    val provider: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresInSeconds: Long,
    val user: AuthUserResponse,
)

@Serializable
data class AuthUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
)

fun IssuedAuthSession.toResponse(): AuthSuccessResponse {
    return AuthSuccessResponse(
        provider = provider,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = expiresInSeconds,
        user = user.toResponse(),
    )
}

fun StoredUser.toResponse(): AuthUserResponse {
    return AuthUserResponse(
        id = id,
        displayName = displayName,
        username = username,
        photoUrl = photoUrl,
        roles = roles.map { it.wireName }.sorted(),
        activeRole = activeRole?.wireName,
        linkedProviders = linkedProviders.map { it.wireName }.sorted(),
    )
}
