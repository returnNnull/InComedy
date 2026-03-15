package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.AuthStateGenerator
import com.bam.incomedy.feature.auth.domain.CredentialAuthService
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

/**
 * Shared MVI auth coordinator for first-party credentials and external providers.
 *
 * The view model owns the transient start/complete state for external auth, emits launch effects for
 * platform adapters, and converts validated backend responses into the single auth UI state.
 */
class AuthViewModel(
    private val credentialAuthService: CredentialAuthService,
    private val socialAuthService: SocialAuthService,
    private val sessionValidationService: SessionValidationService,
    private val sessionTerminationService: SessionTerminationService,
    private val stateGenerator: AuthStateGenerator = RandomAuthStateGenerator(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Coroutine scope that serializes auth side effects outside platform lifecycle classes. */
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** In-memory map of provider -> expected callback state for the current app process. */
    private val pendingStates = mutableMapOf<AuthProviderType, String>()

    /** Mutable backing state for the auth screen and platform wrappers. */
    private val _state = MutableStateFlow(AuthState())

    /** Public immutable auth state stream consumed by platform-specific UI. */
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Mutable backing effect stream for one-off actions such as external auth launch. */
    private val _effects = MutableSharedFlow<AuthEffect>(extraBufferCapacity = 1)

    /** Public immutable effect stream consumed by platform adapters. */
    val effects: SharedFlow<AuthEffect> = _effects.asSharedFlow()

    /** Routes UI intents into the appropriate auth flow branch. */
    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.OnSignInSubmit -> signIn(login = intent.login, password = intent.password)
            is AuthIntent.OnRegisterSubmit -> register(login = intent.login, password = intent.password)
            is AuthIntent.OnProviderClick -> startAuth(intent.provider)
            is AuthIntent.OnAuthCallback -> completeAuth(intent.provider, intent.code, intent.state)
            is AuthIntent.OnAuthFailure -> handleAuthFailure(intent.provider, intent.message)
            is AuthIntent.OnRestoreSessionTokens -> restoreSessionByToken(intent.accessToken, intent.refreshToken)
            is AuthIntent.OnRestoreSessionToken -> restoreSessionByToken(intent.accessToken, null)
            is AuthIntent.OnRestoreSession -> restoreSession(intent.session)
            AuthIntent.OnSignOut -> signOut()
            AuthIntent.OnClearError -> clearError()
        }
    }

    /** Starts an external-provider auth flow and emits the platform launch effect. */
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
                    val url = launchRequest.url.takeIf { it.isNotBlank() }
                        ?: run {
                            _state.update { current ->
                                current.copy(
                                    isLoading = false,
                                    errorMessage = "Auth launch URL is missing for $provider",
                                )
                            }
                            return@fold
                        }
                    _effects.emit(
                        AuthEffect.OpenExternalAuth(
                            provider = provider,
                            url = url,
                            state = launchRequest.state,
                            providerClientId = launchRequest.providerClientId,
                            providerCodeChallenge = launchRequest.providerCodeChallenge,
                            providerScopes = launchRequest.providerScopes,
                        ),
                    )
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

    /** Runs first-party credential sign-in and maps the result into shared auth state. */
    private fun signIn(login: String, password: String) {
        completeCredentialAuth(
            stage = "login",
            provider = AuthProviderType.PASSWORD,
        ) {
            credentialAuthService.signIn(login = login, password = password)
        }
    }

    /** Runs first-party credential registration and maps the result into shared auth state. */
    private fun register(login: String, password: String) {
        completeCredentialAuth(
            stage = "register",
            provider = AuthProviderType.PASSWORD,
        ) {
            credentialAuthService.register(login = login, password = password)
        }
    }

    /**
     * Completes an external auth flow after the mobile platform hands back a callback URL/code.
     *
     * VK is allowed to continue when the callback state is non-blank but the local process forgot the
     * pending state, because the backend still verifies the signed VK `state` as the source of truth.
     */
    private fun completeAuth(provider: AuthProviderType, code: String, state: String) {
        scope.launch {
            AuthFlowLogger.event(
                stage = "complete_auth.callback_received",
                provider = provider,
                details = "statePresent=${state.isNotBlank()}",
            )
            val expectedState = pendingStates[provider]
            val validState = isCallbackStateValid(
                provider = provider,
                expectedState = expectedState,
                callbackState = state,
            )
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

    /** Clears the currently displayed auth error without mutating session state. */
    private fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Shared credential-auth runner for login and registration flows. */
    private fun completeCredentialAuth(
        stage: String,
        provider: AuthProviderType,
        action: suspend () -> Result<AuthSession>,
    ) {
        scope.launch {
            AuthFlowLogger.event(stage = "credentials.$stage.requested", provider = provider)
            _state.update {
                it.copy(
                    isLoading = true,
                    selectedProvider = provider,
                    errorMessage = null,
                )
            }
            action().fold(
                onSuccess = { session ->
                    AuthFlowLogger.event(
                        stage = "credentials.$stage.success",
                        provider = provider,
                        details = "userId=${session.userId}",
                    )
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            session = session,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    AuthFlowLogger.event(
                        stage = "credentials.$stage.failed",
                        provider = provider,
                        details = "reason=${sanitizeForLog(error.message)}",
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

    /**
     * Validates provider callback state against the client-side pending state cache.
     *
     * VK can recover from Android/iOS process recreation because the backend-issued VK state is signed
     * and revalidated server-side, while other providers still require the in-memory match.
     */
    private fun isCallbackStateValid(
        provider: AuthProviderType,
        expectedState: String?,
        callbackState: String,
    ): Boolean {
        return when (provider) {
            AuthProviderType.TELEGRAM -> callbackState.isBlank() || (expectedState != null && expectedState == callbackState)
            AuthProviderType.VK -> callbackState.isNotBlank() && (expectedState == null || expectedState == callbackState)
            else -> expectedState != null && expectedState == callbackState
        }
    }

    /** Applies an explicit auth failure surfaced by a platform-native provider branch. */
    private fun handleAuthFailure(provider: AuthProviderType, message: String) {
        AuthFlowLogger.event(
            stage = "native_auth.failed",
            provider = provider,
            details = "reason=${sanitizeForLog(message)}",
        )
        _state.update {
            it.copy(
                isLoading = false,
                errorMessage = message.take(200).ifBlank { "Unable to complete auth" },
            )
        }
    }

    /** Restores a fully materialized session that was already validated by another layer. */
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

    /** Convenience overload for legacy callers that only have an access token. */
    private fun restoreSessionByToken(accessToken: String) {
        restoreSessionByToken(accessToken = accessToken, refreshToken = null)
    }

    /** Revalidates persisted tokens with the backend and restores the shared auth state on success. */
    private fun restoreSessionByToken(accessToken: String, refreshToken: String?) {
        if (accessToken.isBlank()) {
            scope.launch { _effects.emit(AuthEffect.InvalidateStoredSession) }
            return
        }
        scope.launch {
            AuthFlowLogger.event(stage = "session.restore.requested")
            val validationResult = sessionValidationService.validate(
                accessToken = accessToken,
                refreshToken = refreshToken,
            )
            validationResult.fold(
                onSuccess = { validated ->
                    restoreSession(
                        AuthSession(
                            provider = validated.provider,
                            userId = validated.userId,
                            accessToken = validated.accessToken,
                            refreshToken = validated.refreshToken,
                            user = validated.user,
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

    /** Terminates the current backend session and clears the local shared auth state. */
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

    /** Cancels internal coroutines when the platform wrapper disposes the shared auth coordinator. */
    fun clear() {
        scope.cancel()
    }

    /** Collapses noisy backend/network exceptions into bounded safe diagnostics strings. */
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
