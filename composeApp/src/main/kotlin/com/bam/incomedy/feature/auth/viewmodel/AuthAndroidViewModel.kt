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

/**
 * Android-адаптер общего auth `ViewModel`, который хранит токены в защищенном хранилище
 * и прокидывает callback URL в общую авторизационную логику.
 *
 * @property application Android application context.
 */
class AuthAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая модель авторизации из KMP-слоя. */
    private val sharedViewModel: AuthViewModel = InComedyKoin.getAuthViewModel()

    /** IO-скоуп для работы с защищенным хранилищем и эффектами. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Защищенное хранилище токенов Android. */
    private val securePrefs = createSecurePreferences(application)

    /** Состояние общего auth-потока для Android UI. */
    val state: StateFlow<AuthState> = sharedViewModel.state

    /** Эффекты общего auth-потока для Android UI. */
    val effects: SharedFlow<AuthEffect> = sharedViewModel.effects

    init {
        observeEffects()
        restoreSessionIfPossible()
        persistSessionToken()
    }

    /** Передает интент в общий auth-слой. */
    fun onIntent(intent: AuthIntent) {
        sharedViewModel.onIntent(intent)
    }

    /** Обрабатывает callback URL от внешнего auth-провайдера. */
    fun onAuthCallbackUrl(callbackUrl: String?) {
        AuthFlowLogger.event(
            stage = "android.callback_url.received",
            details = safeAuthCallbackSummary(callbackUrl),
        )
        if (callbackUrl.isNullOrBlank()) {
            AuthFlowLogger.event(
                stage = "android.callback_url.ignored",
                details = "reason=empty",
            )
            return
        }
        val parsed = AuthCallbackParser.parse(callbackUrl)
        if (parsed == null) {
            AuthFlowLogger.event(
                stage = "android.callback_url.ignored",
                details = "reason=unrecognized ${safeAuthCallbackSummary(callbackUrl)}",
            )
            return
        }
        AuthFlowLogger.event(
            stage = "android.callback_url.parsed",
            provider = parsed.provider,
            details = "statePresent=${parsed.state.isNotBlank()} ${safeAuthCallbackSummary(callbackUrl)}",
        )
        AuthFlowLogger.event(
            stage = "android.callback_url.forwarded",
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

    /** Пытается восстановить сессию из локально сохраненных токенов. */
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

    /** Сохраняет токены после успешного изменения общего auth-состояния. */
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

    /** Следит за эффектами invalidation и удаляет локальные токены при выходе. */
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
    }

    /** Сохраняет access token в защищенное хранилище. */
    private fun saveAccessToken(token: String) {
        securePrefs?.edit { putString(KEY_ACCESS_TOKEN, token) }
    }

    /** Удаляет access token из защищенного хранилища. */
    private fun deleteAccessToken() {
        securePrefs?.edit { remove(KEY_ACCESS_TOKEN) }
    }

    /** Сохраняет refresh token в защищенное хранилище. */
    private fun saveRefreshToken(token: String) {
        securePrefs?.edit { putString(KEY_REFRESH_TOKEN, token) }
    }

    /** Удаляет refresh token из защищенного хранилища. */
    private fun deleteRefreshToken() {
        securePrefs?.edit { remove(KEY_REFRESH_TOKEN) }
    }

    /** Загружает access token из защищенного хранилища. */
    private fun loadAccessToken(): String? {
        return securePrefs?.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    /** Загружает refresh token из защищенного хранилища. */
    private fun loadRefreshToken(): String? {
        return securePrefs?.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    /** Создает Android secure storage или возвращает `null`, если платформа недоступна. */
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
        /** Ключ сохранения access token. */
        const val KEY_ACCESS_TOKEN = "access_token"

        /** Ключ сохранения refresh token. */
        const val KEY_REFRESH_TOKEN = "refresh_token"

        /** Имя защищенного набора настроек для auth-сессии. */
        const val SECURE_PREFS_NAME = "auth_session_secure"
    }
}
