package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

sealed interface AuthEffect {
    /**
     * One-off effect that tells the platform layer to launch an external or provider-native auth step.
     *
     * @property provider Provider chosen by the user.
     * @property url Browser launch URL, if the current provider flow still needs one.
     * @property state Signed state token that must come back to the shared completion flow.
     * @property providerClientId Provider client id required for native/mobile SDK auth, if present.
     * @property providerCodeChallenge PKCE challenge required for native/mobile SDK auth, if present.
     * @property providerScopes Provider scopes required for native/mobile SDK auth.
     */
    data class OpenExternalAuth(
        val provider: AuthProviderType,
        val url: String,
        val state: String = "",
        val providerClientId: String? = null,
        val providerCodeChallenge: String? = null,
        val providerScopes: Set<String> = emptySet(),
    ) : AuthEffect

    /** One-off effect that invalidates locally stored access/refresh tokens after logout or restore failure. */
    object InvalidateStoredSession : AuthEffect
}
