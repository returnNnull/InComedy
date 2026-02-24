package com.bam.incomedy.shared.auth

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.auth.providers.AuthCallbackParser
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.bridge.BridgeHandle
import com.bam.incomedy.shared.bridge.CompositeBridgeHandle
import com.bam.incomedy.shared.di.InComedyKoin

class AuthFeatureBridge(
    private val viewModel: AuthViewModel = InComedyKoin.getAuthViewModel(),
) : BaseFeatureBridge() {

    fun currentState(): AuthUiStateSnapshot = viewModel.state.value.toSnapshot()

    fun observeState(onState: (AuthUiStateSnapshot) -> Unit): BridgeHandle {
        return observeState(
            stateFlow = viewModel.state,
            mapper = { state -> state.toSnapshot() },
            onState = onState,
        )
    }

    fun observeOpenAuthUrl(onOpenUrl: (String) -> Unit): BridgeHandle {
        return observeEffect(effectFlow = viewModel.effects) { effect ->
            if (effect is AuthEffect.OpenExternalAuth) {
                onOpenUrl(effect.url)
            }
        }
    }

    fun bind(
        onState: (AuthUiStateSnapshot) -> Unit,
        onOpenUrl: (String) -> Unit,
        onInvalidateSession: () -> Unit,
    ): BridgeHandle {
        return CompositeBridgeHandle(
            handles = listOf(
                observeState(onState),
                observeOpenAuthUrl(onOpenUrl),
                observeEffect(effectFlow = viewModel.effects) { effect ->
                    if (effect is AuthEffect.InvalidateStoredSession) {
                        onInvalidateSession()
                    }
                },
            ),
        )
    }

    fun startAuth(providerKey: String) {
        val provider = providerKey.toProviderType() ?: return
        viewModel.onIntent(AuthIntent.OnProviderClick(provider))
    }

    fun completeAuth(providerKey: String, code: String, state: String) {
        val provider = providerKey.toProviderType() ?: return
        viewModel.onIntent(AuthIntent.OnAuthCallback(provider = provider, code = code, state = state))
    }

    fun completeAuthFromCallbackUrl(callbackUrl: String) {
        val parsed = AuthCallbackParser.parse(callbackUrl) ?: return
        viewModel.onIntent(
            AuthIntent.OnAuthCallback(
                provider = parsed.provider,
                code = parsed.code,
                state = parsed.state,
            ),
        )
    }

    fun clearError() {
        viewModel.onIntent(AuthIntent.OnClearError)
    }

    fun restoreSession(providerKey: String, userId: String, accessToken: String) {
        val provider = providerKey.toProviderType() ?: return
        viewModel.onIntent(
            AuthIntent.OnRestoreSession(
                session = AuthSession(
                    provider = provider,
                    userId = userId,
                    accessToken = accessToken,
                ),
            ),
        )
    }

    fun restoreSessionToken(accessToken: String) {
        viewModel.onIntent(AuthIntent.OnRestoreSessionToken(accessToken))
    }

    override fun dispose() {
        super.dispose()
        viewModel.clear()
    }
}

private fun AuthState.toSnapshot(): AuthUiStateSnapshot {
    return AuthUiStateSnapshot(
        isLoading = isLoading,
        selectedProviderKey = selectedProvider?.toKey(),
        errorMessage = errorMessage,
        isAuthorized = isAuthorized,
        authorizedProviderKey = session?.provider?.toKey(),
        authorizedUserId = session?.userId,
        authorizedAccessToken = session?.accessToken,
    )
}

private fun AuthProviderType.toKey(): String {
    return when (this) {
        AuthProviderType.VK -> "vk"
        AuthProviderType.TELEGRAM -> "telegram"
        AuthProviderType.GOOGLE -> "google"
    }
}

private fun String.toProviderType(): AuthProviderType? {
    return when (trim().lowercase()) {
        "vk" -> AuthProviderType.VK
        "telegram", "tg" -> AuthProviderType.TELEGRAM
        "google" -> AuthProviderType.GOOGLE
        else -> null
    }
}
