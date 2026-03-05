package com.bam.incomedy.feature.auth.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.auth.providers.AuthCallbackParser
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val sharedViewModel: AuthViewModel = InComedyKoin.getAuthViewModel()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val securePrefs = createSecurePreferences(application)

    val state: StateFlow<AuthState> = sharedViewModel.state
    val effects: SharedFlow<AuthEffect> = sharedViewModel.effects

    init {
        observeEffects()
        restoreSessionIfPossible()
        persistSessionToken()
    }

    fun onIntent(intent: AuthIntent) {
        sharedViewModel.onIntent(intent)
    }

    fun onAuthCallbackUrl(callbackUrl: String?) {
        AuthFlowLogger.event(
            stage = "android.callback_url.received",
            details = "hasUrl=${!callbackUrl.isNullOrBlank()}",
        )
        val parsed = callbackUrl?.let { AuthCallbackParser.parse(it) } ?: return
        AuthFlowLogger.event(
            stage = "android.callback_url.parsed",
            provider = parsed.provider,
            details = "statePresent=${parsed.state.isNotBlank()}",
        )
        sharedViewModel.onIntent(
            AuthIntent.OnAuthCallback(
                provider = parsed.provider,
                code = parsed.code,
                state = parsed.state,
            ),
        )
    }

    private fun restoreSessionIfPossible() {
        val accessToken = loadAccessToken() ?: return
        val refreshToken = loadRefreshToken()
        sharedViewModel.onIntent(
            AuthIntent.OnRestoreSessionTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
            ),
        )
    }

    private fun persistSessionToken() {
        ioScope.launch {
            state.collectLatest { current ->
                val token = current.session?.accessToken
                if (token.isNullOrBlank()) return@collectLatest
                saveAccessToken(token)
                val refreshToken = current.session?.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    deleteRefreshToken()
                } else {
                    saveRefreshToken(refreshToken)
                }
            }
        }
    }

    private fun observeEffects() {
        ioScope.launch {
            effects.collectLatest { effect ->
                if (effect is AuthEffect.InvalidateStoredSession) {
                    deleteAccessToken()
                    deleteRefreshToken()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ioScope.cancel()
        sharedViewModel.clear()
    }

    private fun saveAccessToken(token: String) {
        securePrefs?.edit { putString(KEY_ACCESS_TOKEN, token) }
    }

    private fun deleteAccessToken() {
        securePrefs?.edit { remove(KEY_ACCESS_TOKEN) }
    }

    private fun saveRefreshToken(token: String) {
        securePrefs?.edit { putString(KEY_REFRESH_TOKEN, token) }
    }

    private fun deleteRefreshToken() {
        securePrefs?.edit { remove(KEY_REFRESH_TOKEN) }
    }

    private fun loadAccessToken(): String? {
        return securePrefs?.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    private fun loadRefreshToken(): String? {
        return securePrefs?.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    private fun createSecurePreferences(application: Application): SharedPreferences? {
        return runCatching {
            val masterKey = MasterKey.Builder(application)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                application,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { error ->
            AuthFlowLogger.event(
                stage = "android.session.secure_storage.unavailable",
                details = "reason=${error.message ?: "unknown"}",
            )
            null
        }
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val SECURE_PREFS_NAME = "auth_session_secure"
    }
}
