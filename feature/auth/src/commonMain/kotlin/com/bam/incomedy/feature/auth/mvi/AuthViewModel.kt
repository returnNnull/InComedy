package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthStateGenerator
import com.bam.incomedy.feature.auth.domain.RandomAuthStateGenerator
import com.bam.incomedy.feature.auth.domain.SocialAuthService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val socialAuthService: SocialAuthService,
    private val stateGenerator: AuthStateGenerator = RandomAuthStateGenerator(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val pendingStates = mutableMapOf<AuthProviderType, String>()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AuthEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AuthEffect> = _effects.asSharedFlow()

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.OnProviderClick -> startAuth(intent.provider)
            is AuthIntent.OnAuthCallback -> completeAuth(intent.provider, intent.code, intent.state)
            AuthIntent.OnClearError -> clearError()
        }
    }

    private fun startAuth(provider: AuthProviderType) {
        scope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    selectedProvider = provider,
                    errorMessage = null,
                )
            }

            val stateToken = stateGenerator.next()
            val result = socialAuthService.requestLaunch(provider, stateToken)
            result.fold(
                onSuccess = { launchRequest ->
                    pendingStates[provider] = launchRequest.state
                    _effects.emit(AuthEffect.OpenExternalAuth(provider, launchRequest.url))
                    _state.update { current -> current.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to start auth",
                        )
                    }
                },
            )
        }
    }

    private fun completeAuth(provider: AuthProviderType, code: String, state: String) {
        scope.launch {
            val expectedState = pendingStates[provider]
            val validState = when (provider) {
                AuthProviderType.TELEGRAM -> expectedState != null && (state.isBlank() || expectedState == state)
                else -> expectedState != null && expectedState == state
            }
            if (!validState) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Invalid auth state for $provider",
                    )
                }
                return@launch
            }

            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = socialAuthService.complete(provider, code, state)
            result.fold(
                onSuccess = { session ->
                    pendingStates.remove(provider)
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            session = session,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to complete auth",
                        )
                    }
                },
            )
        }
    }

    private fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun clear() {
        scope.cancel()
    }
}
