package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionViewModel(
    private val authViewModel: AuthViewModel,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        scope.launch {
            authViewModel.state.collect { authState ->
                val session = authState.session
                _state.update {
                    SessionState(
                        isAuthorized = session != null,
                        provider = session?.provider,
                        accessToken = session?.accessToken,
                        refreshToken = session?.refreshToken,
                        userId = session?.userId,
                        displayName = session?.user?.displayName,
                        username = session?.user?.username,
                        photoUrl = session?.user?.photoUrl,
                    )
                }
            }
        }
    }

    fun restoreSessionToken(accessToken: String) {
        authViewModel.onIntent(AuthIntent.OnRestoreSessionToken(accessToken))
    }

    fun signOut() {
        authViewModel.onIntent(AuthIntent.OnSignOut)
    }

    fun clear() {
        scope.cancel()
    }
}
