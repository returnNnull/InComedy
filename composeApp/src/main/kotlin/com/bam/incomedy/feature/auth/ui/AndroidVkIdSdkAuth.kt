package com.bam.incomedy.feature.auth.ui

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner
import com.bam.incomedy.BuildConfig
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
import com.vk.id.AccessToken
import com.vk.id.VKID
import com.vk.id.VKIDAuthFail
import com.vk.id.auth.AuthCodeData
import com.vk.id.auth.VKIDAuthCallback
import com.vk.id.auth.VKIDAuthParams

/**
 * Runtime Android config for the optional VK ID SDK path.
 *
 * The config is derived from Gradle/env-backed BuildConfig fields so local builds can keep the SDK
 * disabled without breaking the browser fallback path.
 */
internal data class AndroidVkIdSdkConfig(
    val clientId: String,
    val redirectHost: String,
    val redirectScheme: String,
    val isEnabled: Boolean,
) {
    /** Returns `true` when the current auth effect can be completed through the local VK SDK config. */
    fun canHandle(effect: AuthEffect.OpenExternalAuth): Boolean {
        return isEnabled &&
            effect.provider == AuthProviderType.VK &&
            effect.providerClientId == clientId &&
            !effect.providerCodeChallenge.isNullOrBlank() &&
            effect.state.isNotBlank()
    }

    /** Returns a bounded reason code when SDK launch should fall back to browser launch instead. */
    fun fallbackReason(effect: AuthEffect.OpenExternalAuth): String {
        return when {
            !isEnabled -> "sdk_disabled"
            effect.provider != AuthProviderType.VK -> "unsupported_provider"
            effect.providerClientId.isNullOrBlank() -> "missing_server_client"
            effect.providerClientId != clientId -> "client_id_mismatch"
            effect.providerCodeChallenge.isNullOrBlank() -> "missing_code_challenge"
            effect.state.isBlank() -> "missing_state"
            else -> "unsupported"
        }
    }
}

/**
 * Android-only VK SDK launcher that turns SDK auth-code callbacks into the shared callback-URL flow.
 */
internal object AndroidVkIdSdkAuth {
    /** Returns the current Android VK SDK runtime config derived from BuildConfig values. */
    fun runtimeConfig(): AndroidVkIdSdkConfig {
        return AndroidVkIdSdkConfig(
            clientId = BuildConfig.VK_ANDROID_CLIENT_ID,
            redirectHost = BuildConfig.VK_ANDROID_REDIRECT_HOST,
            redirectScheme = BuildConfig.VK_ANDROID_REDIRECT_SCHEME,
            isEnabled = BuildConfig.VK_ANDROID_SDK_ENABLED,
        )
    }

    /**
     * Attempts to launch VK auth through the official Android SDK.
     *
     * Returns `true` only when SDK auth was started successfully. Returning `false` means the caller
     * should fall back to the existing browser/public-callback launch path.
     */
    fun start(
        context: Context,
        effect: AuthEffect.OpenExternalAuth,
        onAuthCallbackUrl: (String) -> Unit,
        onAuthFailure: (String) -> Unit,
    ): Boolean {
        val runtimeConfig = runtimeConfig()
        if (!runtimeConfig.canHandle(effect)) {
            AuthFlowLogger.event(
                stage = "android.vk_sdk.fallback",
                provider = effect.provider,
                details = "reason=${runtimeConfig.fallbackReason(effect)}",
            )
            return false
        }

        val lifecycleOwner = context.findComponentActivity()
        if (lifecycleOwner == null) {
            AuthFlowLogger.event(
                stage = "android.vk_sdk.fallback",
                provider = effect.provider,
                details = "reason=missing_activity",
            )
            return false
        }

        return runCatching {
            VKID.instance.authorize(
                lifecycleOwner = lifecycleOwner,
                callback = callbackFor(
                    effect = effect,
                    onAuthCallbackUrl = onAuthCallbackUrl,
                    onAuthFailure = onAuthFailure,
                ),
                params = VKIDAuthParams {
                    state = effect.state
                    codeChallenge = effect.providerCodeChallenge
                    scopes = effect.providerScopes
                    useOAuthProviderIfPossible = true
                },
            )
        }.onSuccess {
            AuthFlowLogger.event(
                stage = "android.vk_sdk.authorize.started",
                provider = effect.provider,
                details = "scopes=${if (effect.providerScopes.isEmpty()) "default" else effect.providerScopes.size}",
            )
        }.onFailure { error ->
            AuthFlowLogger.event(
                stage = "android.vk_sdk.fallback",
                provider = effect.provider,
                details = "reason=start_failed error=${sanitizeLogReason(error.message)}",
            )
        }.isSuccess
    }

    /** Creates the VK SDK callback that feeds auth-code results back into the shared callback flow. */
    private fun callbackFor(
        effect: AuthEffect.OpenExternalAuth,
        onAuthCallbackUrl: (String) -> Unit,
        onAuthFailure: (String) -> Unit,
    ): VKIDAuthCallback {
        return object : VKIDAuthCallback {
            override fun onAuth(accessToken: AccessToken) {
                AuthFlowLogger.event(
                    stage = "android.vk_sdk.authorize.unexpected_access_token",
                    provider = effect.provider,
                    details = "tokenPresent=${accessToken.token.isNotBlank()}",
                )
                onAuthFailure("VK SDK did not return an auth code")
            }

            override fun onAuthCode(data: AuthCodeData, isCompletion: Boolean) {
                AuthFlowLogger.event(
                    stage = "android.vk_sdk.authorize.code_received",
                    provider = effect.provider,
                    details = "deviceIdPresent=${data.deviceId.isNotBlank()} completion=$isCompletion",
                )
                onAuthCallbackUrl(
                    buildVkSdkCallbackUrl(
                        code = data.code,
                        state = effect.state,
                        deviceId = data.deviceId,
                    ),
                )
            }

            override fun onFail(fail: VKIDAuthFail) {
                AuthFlowLogger.event(
                    stage = "android.vk_sdk.authorize.failed",
                    provider = effect.provider,
                    details = "reason=${fail.safeReason()}",
                )
                onAuthFailure("VK auth failed: ${fail.safeReason()}")
            }
        }
    }
}

/** Builds a synthetic deep link so the shared VK callback parser can reuse the existing completion path. */
internal fun buildVkSdkCallbackUrl(
    code: String,
    state: String,
    deviceId: String,
): String {
    return Uri.Builder()
        .scheme("incomedy")
        .authority("auth")
        .appendPath("vk")
        .appendPath("sdk")
        .appendQueryParameter("code", code)
        .appendQueryParameter("state", state)
        .appendQueryParameter("device_id", deviceId)
        .appendQueryParameter("client_source", "android_sdk")
        .build()
        .toString()
}

/** Recursively unwraps a `Context` until the hosting `ComponentActivity` is found. */
private tailrec fun Context.findComponentActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}

/** Converts VK SDK failure types into bounded low-cardinality log reasons. */
private fun VKIDAuthFail.safeReason(): String {
    return when (this) {
        is VKIDAuthFail.Canceled -> "canceled"
        is VKIDAuthFail.FailedApiCall -> "failed_api_call"
        is VKIDAuthFail.FailedOAuth -> "failed_oauth"
        is VKIDAuthFail.FailedOAuthState -> "failed_oauth_state"
        is VKIDAuthFail.FailedRedirectActivity -> "failed_redirect_activity"
        is VKIDAuthFail.NoBrowserAvailable -> "no_browser_available"
    }
}

/** Bounds free-form SDK failure text before it is written to client logs. */
private fun sanitizeLogReason(reason: String?): String {
    return reason
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"
}
