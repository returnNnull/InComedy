package com.bam.incomedy.feature.auth.ui

import android.net.Uri
import com.bam.incomedy.BuildConfig
import com.vk.id.VKIDAuthFail
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Runtime-конфиг documented Android VK SDK path.
 *
 * Конфиг берется из `BuildConfig`, чтобы OneTap можно было безопасно отключать в сборках без
 * локальной VK Android конфигурации, сохраняя browser fallback.
 */
internal data class AndroidVkIdSdkConfig(
    val clientId: String,
    val redirectHost: String,
    val redirectScheme: String,
    val requestedScopes: Set<String>,
    val isEnabled: Boolean,
) {
    /** Проверяет, можно ли рендерить официальный VK OneTap без скрытого fallback на другой transport. */
    fun canUseOneTap(): Boolean {
        return isEnabled &&
            clientId.isNotBlank() &&
            redirectHost.isNotBlank() &&
            redirectScheme.isNotBlank()
    }

    /** Возвращает low-cardinality причину, по которой OneTap сейчас недоступен. */
    fun unavailableReason(): String {
        return when {
            !isEnabled -> "sdk_disabled"
            clientId.isBlank() -> "missing_client_id"
            redirectHost.isBlank() -> "missing_redirect_host"
            redirectScheme.isBlank() -> "missing_redirect_scheme"
            else -> "unknown"
        }
    }
}

/**
 * Локально сгенерированная Android VK auth-попытка для documented SDK/backend exchange flow.
 *
 * `state`, `codeVerifier` и `codeChallenge` принадлежат клиенту и живут только в приложении до
 * завершения конкретной попытки авторизации.
 */
internal data class AndroidVkIdAuthAttempt(
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
)

/** Android-only helper для documented VK OneTap flow. */
internal object AndroidVkIdSdkAuth {
    /** Возвращает текущий runtime-конфиг Android VK SDK из `BuildConfig`. */
    fun runtimeConfig(): AndroidVkIdSdkConfig {
        return AndroidVkIdSdkConfig(
            clientId = BuildConfig.VK_ANDROID_CLIENT_ID,
            redirectHost = BuildConfig.VK_ANDROID_REDIRECT_HOST,
            redirectScheme = BuildConfig.VK_ANDROID_REDIRECT_SCHEME,
            requestedScopes = BuildConfig.VK_ID_SCOPE.toScopeSet(),
            isEnabled = BuildConfig.VK_ANDROID_SDK_ENABLED,
        )
    }

    /** Создает новую documented auth-попытку с локальными `state` и PKCE-параметрами. */
    fun newAuthAttempt(): AndroidVkIdAuthAttempt {
        val codeVerifier = randomUrlSafeValue(byteCount = 48)
        val state = randomUrlSafeValue(byteCount = 24)
        val codeChallenge = codeVerifier
            .toByteArray(Charsets.US_ASCII)
            .sha256()
            .toUrlSafeBase64()
        return AndroidVkIdAuthAttempt(
            state = state,
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
        )
    }
}

private val secureRandom = SecureRandom()

/**
 * Собирает synthetic deep link для shared VK callback parser.
 *
 * URL не покидает приложение: он используется только как единый транспорт callback payload в общий
 * auth-flow и передает локально сгенерированный `codeVerifier` на backend verify.
 */
internal fun buildVkSdkCallbackUrl(
    code: String,
    state: String,
    deviceId: String,
    codeVerifier: String,
): String {
    return Uri.Builder()
        .scheme("incomedy")
        .authority("auth")
        .appendPath("vk")
        .appendPath("sdk")
        .appendQueryParameter("code", code)
        .appendQueryParameter("state", state)
        .appendQueryParameter("device_id", deviceId)
        .appendQueryParameter("code_verifier", codeVerifier)
        .appendQueryParameter("client_source", "android_sdk")
        .build()
        .toString()
}

/** Преобразует VK SDK failure types в bounded low-cardinality log reasons. */
internal fun VKIDAuthFail.safeReason(): String {
    return when (this) {
        is VKIDAuthFail.Canceled -> "canceled"
        is VKIDAuthFail.FailedApiCall -> "failed_api_call"
        is VKIDAuthFail.FailedOAuth -> "failed_oauth"
        is VKIDAuthFail.FailedOAuthState -> "failed_oauth_state"
        is VKIDAuthFail.FailedRedirectActivity -> "failed_redirect_activity"
        is VKIDAuthFail.NoBrowserAvailable -> "no_browser_available"
    }
}

/** Разбивает runtime scope config на детерминированный набор токенов для VK OneTap. */
private fun String.toScopeSet(): Set<String> {
    return split(',', ' ')
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
}

/** Генерирует короткое URL-safe значение без padding для `state` и PKCE verifier. */
private fun randomUrlSafeValue(byteCount: Int): String {
    val rawBytes = ByteArray(byteCount)
    secureRandom.nextBytes(rawBytes)
    return rawBytes.toUrlSafeBase64()
}

/** Считает SHA-256 digest для PKCE code challenge. */
private fun ByteArray.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}

/** Кодирует байты в base64url без `=` padding, как требует PKCE/VK SDK flow. */
private fun ByteArray.toUrlSafeBase64(): String {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(this)
}
