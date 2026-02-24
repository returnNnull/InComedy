package com.bam.incomedy.feature.auth.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
    private val legacyPrefs = application.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
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
        val token = loadAccessToken() ?: return
        sharedViewModel.onIntent(AuthIntent.OnRestoreSessionToken(token))
    }

    private fun persistSessionToken() {
        ioScope.launch {
            state.collectLatest { current ->
                val token = current.session?.accessToken
                if (token.isNullOrBlank()) return@collectLatest
                saveAccessToken(token)
            }
        }
    }

    private fun observeEffects() {
        ioScope.launch {
            effects.collectLatest { effect ->
                if (effect is AuthEffect.InvalidateStoredSession) {
                    deleteAccessToken()
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
        if (securePrefs != null) {
            securePrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        } else {
            legacyPrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        }
    }

    private fun deleteAccessToken() {
        securePrefs?.edit()?.remove(KEY_ACCESS_TOKEN)?.apply()
        legacyPrefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    private fun loadAccessToken(): String? {
        val secureToken = securePrefs?.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        if (secureToken != null) return secureToken

        if (securePrefs == null) {
            return legacyPrefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        }

        // One-time migration from legacy plain SharedPreferences.
        val legacyToken = legacyPrefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, legacyToken).apply()
        legacyPrefs.edit().remove(KEY_ACCESS_TOKEN).apply()
        AuthFlowLogger.event(stage = "android.session.token.migrated_to_secure_storage")
        return legacyToken
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
        const val LEGACY_PREFS_NAME = "auth_session"
        const val SECURE_PREFS_NAME = "auth_session_secure"
    }
}
