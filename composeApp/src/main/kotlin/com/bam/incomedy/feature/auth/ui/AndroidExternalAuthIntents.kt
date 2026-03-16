package com.bam.incomedy.feature.auth.ui

import android.content.Intent
import android.net.Uri
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect

internal data class ExternalAuthLaunchPlan(
    val primaryIntent: Intent,
    val fallbackIntent: Intent? = null,
    val launchMode: String,
)

internal object AndroidExternalAuthIntents {
    private const val VK_ANDROID_PACKAGE = "com.vkontakte.android"

    fun plan(
        effect: AuthEffect.OpenExternalAuth,
        uri: Uri,
        canResolveIntent: (Intent) -> Boolean,
    ): ExternalAuthLaunchPlan {
        val browserIntent = browserIntent(uri)
        if (effect.provider != AuthProviderType.VK) {
            return ExternalAuthLaunchPlan(
                primaryIntent = browserIntent,
                launchMode = "browser",
            )
        }

        val vkAppIntent = browserIntent(uri).apply {
            `package` = VK_ANDROID_PACKAGE
        }
        return if (canResolveIntent(vkAppIntent)) {
            ExternalAuthLaunchPlan(
                primaryIntent = vkAppIntent,
                fallbackIntent = browserIntent,
                launchMode = "vk_app",
            )
        } else {
            ExternalAuthLaunchPlan(
                primaryIntent = browserIntent,
                launchMode = "browser_no_vk_app",
            )
        }
    }

    private fun browserIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
