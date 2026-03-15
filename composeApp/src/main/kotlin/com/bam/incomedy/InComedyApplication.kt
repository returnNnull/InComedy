package com.bam.incomedy

import android.app.Application
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
import com.vk.id.VKID

/**
 * Android application entry point that initializes app-wide SDK integrations before any auth screen opens.
 */
class InComedyApplication : Application() {

    /** Initializes optional Android-only SDK integrations that are configured through local secrets. */
    override fun onCreate() {
        super.onCreate()
        initializeVkIdSdk()
    }

    /** Initializes VK ID SDK only when local Android client credentials are available. */
    private fun initializeVkIdSdk() {
        if (!BuildConfig.VK_ANDROID_SDK_ENABLED) {
            AuthFlowLogger.event(
                stage = "android.vk_sdk.init_skipped",
                details = "reason=missing_client_config",
            )
            return
        }

        runCatching {
            VKID.init(this)
        }.onSuccess {
            AuthFlowLogger.event(
                stage = "android.vk_sdk.initialized",
                details = "redirectHost=${BuildConfig.VK_ANDROID_REDIRECT_HOST}",
            )
        }.onFailure { error ->
            AuthFlowLogger.event(
                stage = "android.vk_sdk.init_failed",
                details = "reason=${sanitizeLogReason(error.message)}",
            )
        }
    }
}

/** Bounds a failure reason before writing it to client logs. */
private fun sanitizeLogReason(reason: String?): String {
    return reason
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"
}
