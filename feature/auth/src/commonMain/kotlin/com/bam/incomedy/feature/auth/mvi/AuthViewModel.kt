package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.AuthStateGenerator
import com.bam.incomedy.feature.auth.domain.RandomAuthStateGenerator
import com.bam.incomedy.feature.auth.domain.SessionTerminationService
import com.bam.incomedy.feature.auth.domain.SessionValidationException
import com.bam.incomedy.feature.auth.domain.SessionValidationFailureReason
import com.bam.incomedy.feature.auth.domain.SessionValidationService
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
    private val sessionValidationService: SessionValidationService,
    private val sessionTerminationService: SessionTerminationService,
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
            is AuthIntent.OnRestoreSessionToken -> restoreSessionByToken(intent.accessToken)
            is AuthIntent.OnRestoreSession -> restoreSession(intent.session)
            AuthIntent.OnSignOut -> signOut()
            AuthIntent.OnClearError -> clearError()
        }
    }

    private fun startAuth(provider: AuthProviderType) {
        scope.launch {
            AuthFlowLogger.event(stage = "start_auth.requested", provider = provider)
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
                    AuthFlowLogger.event(stage = "start_auth.launch_url_ready", provider = provider)
                    pendingStates[provider] = launchRequest.state
                    _effects.emit(AuthEffect.OpenExternalAuth(provider, launchRequest.url))
                    _state.update { current -> current.copy(isLoading = false) }
                },
                onFailure = { error ->
                    AuthFlowLogger.event(
                        stage = "start_auth.failed",
                        provider = provider,
                        details = "reason=${error.message ?: "unknown"}",
                    )
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
            AuthFlowLogger.event(
                stage = "complete_auth.callback_received",
                provider = provider,
                details = "statePresent=${state.isNotBlank()}",
            )
            val expectedState = pendingStates[provider]
            val validState = when (provider) {
                AuthProviderType.TELEGRAM -> state.isBlank() || (expectedState != null && expectedState == state)
                else -> expectedState != null && expectedState == state
            }
            if (!validState) {
                AuthFlowLogger.event(stage = "complete_auth.invalid_state", provider = provider)
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
                    AuthFlowLogger.event(
                        stage = "complete_auth.success",
                        provider = provider,
                        details = "userId=${session.userId}",
                    )
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
                    val safeReason = sanitizeForLog(error.message)
                    AuthFlowLogger.event(
                        stage = "complete_auth.failed",
                        provider = provider,
                        details = "reason=$safeReason",
                    )
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = error.message?.take(200) ?: "Unable to complete auth",
                        )
                    }
                },
            )
        }
    }

    private fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun restoreSession(session: AuthSession) {
        AuthFlowLogger.event(
            stage = "session.restore.success",
            provider = session.provider,
            details = "userId=${session.userId}",
        )
        _state.update {
            it.copy(
                isLoading = false,
                session = session,
                errorMessage = null,
                selectedProvider = session.provider,
            )
        }
    }

    private fun restoreSessionByToken(accessToken: String) {
        if (accessToken.isBlank()) {
            scope.launch { _effects.emit(AuthEffect.InvalidateStoredSession) }
            return
        }
        scope.launch {
            AuthFlowLogger.event(stage = "session.restore.requested")
            val validationResult = sessionValidationService.validate(accessToken)
            validationResult.fold(
                onSuccess = { validated ->
                    restoreSession(
                        AuthSession(
                            provider = validated.provider,
                            userId = validated.userId,
                            accessToken = validated.accessToken,
                        ),
                    )
                },
                onFailure = { error ->
                    val validationException = error as? SessionValidationException
                    AuthFlowLogger.event(
                        stage = "session.restore.failed",
                        details = "reason=${sanitizeForLog(error.message)}",
                    )
                    if (validationException?.reason == SessionValidationFailureReason.UNAUTHORIZED) {
                        _effects.emit(AuthEffect.InvalidateStoredSession)
                    }
                },
            )
        }
    }

    private fun signOut() {
        scope.launch {
            val token = state.value.session?.accessToken
            if (!token.isNullOrBlank()) {
                sessionTerminationService.terminate(token).onFailure { error ->
                    AuthFlowLogger.event(
                        stage = "session.logout.failed",
                        details = "reason=${sanitizeForLog(error.message)}",
                    )
                }
            }
            _state.update { it.copy(session = null, isLoading = false, errorMessage = null) }
            _effects.emit(AuthEffect.InvalidateStoredSession)
            AuthFlowLogger.event(stage = "session.logout.success")
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun sanitizeForLog(raw: String?): String {
        if (raw.isNullOrBlank()) return "unknown"
        val lower = raw.lowercase()
        return when {
            "telegram auth hash" in lower -> "telegram_auth_hash_invalid"
            "illegal input" in lower -> "backend_response_parse_error"
            "unable to resolve host" in lower -> "network_dns_error"
            "timeout" in lower -> "network_timeout"
            else -> raw.take(120)
        }
    }
}
