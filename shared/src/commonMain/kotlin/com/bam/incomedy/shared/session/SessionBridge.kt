package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

class SessionBridge(
    private val viewModel: SessionViewModel = InComedyKoin.getSessionViewModel(),
) : BaseFeatureBridge() {

    fun currentState(): SessionStateSnapshot = viewModel.state.value.toSnapshot()

    fun observeState(onState: (SessionStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    fun signOut() {
        viewModel.signOut()
    }

    fun restoreSessionToken(accessToken: String) {
        viewModel.restoreSessionToken(accessToken)
    }
}

private fun SessionState.toSnapshot(): SessionStateSnapshot {
    return SessionStateSnapshot(
        isAuthorized = isAuthorized,
        providerKey = provider?.toKey(),
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
        displayName = displayName,
        username = username,
        photoUrl = photoUrl,
    )
}

private fun AuthProviderType.toKey(): String {
    return when (this) {
        AuthProviderType.VK -> "vk"
        AuthProviderType.TELEGRAM -> "telegram"
        AuthProviderType.GOOGLE -> "google"
    }
}
