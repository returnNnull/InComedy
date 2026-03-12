package com.bam.incomedy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.feature.auth.viewmodel.safeAuthCallbackSummary
import com.bam.incomedy.viewmodel.AndroidViewModelFactories

/**
 * Корневая Android activity, которая поднимает Compose-приложение и принимает auth callback intent.
 */
class MainActivity : ComponentActivity() {
    /** Android-адаптер auth-состояния, создаваемый через явную фабрику без дефолтной рефлексии. */
    private val authViewModel: AuthAndroidViewModel by viewModels {
        AndroidViewModelFactories.auth(application)
    }

    /** Инициализирует системные insets, пробрасывает callback URL и запускает корневой Compose UI. */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AuthFlowLogger.event(
            stage = "android.activity.on_create",
            details = "hasSavedState=${savedInstanceState != null} action=${intent?.action ?: "n/a"} ${safeAuthCallbackSummary(intent?.dataString)}",
        )
        authViewModel.onAuthCallbackUrl(intent?.dataString)
        setContent {
            App(authViewModel = authViewModel)
        }
    }

    /** Передает новый auth callback в уже созданный `AuthAndroidViewModel`. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AuthFlowLogger.event(
            stage = "android.activity.on_new_intent",
            details = "action=${intent.action ?: "n/a"} ${safeAuthCallbackSummary(intent.dataString)}",
        )
        authViewModel.onAuthCallbackUrl(intent.dataString)
    }
}

/** Превью корневого Android Compose-контейнера. */
@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
