package com.bam.incomedy

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.navigation.AppNavHost
import com.bam.incomedy.viewmodel.AndroidViewModelFactories

/**
 * Корневой Compose-контейнер Android-приложения.
 *
 * @param authViewModel Android-адаптер авторизации. По умолчанию создается через явную фабрику.
 */
@Composable
fun App(
    authViewModel: AuthAndroidViewModel = rememberAuthAndroidViewModel(),
) {
    MaterialTheme {
        AppNavHost(
            authViewModel = authViewModel,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
        )
    }
}

/**
 * Возвращает Android auth `ViewModel` через явную фабрику вместо дефолтного рефлексивного пути.
 */
@Composable
private fun rememberAuthAndroidViewModel(): AuthAndroidViewModel {
    /** Application context, необходимый для Android-адаптера авторизации. */
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        factory = AndroidViewModelFactories.auth(application),
    )
}

/** Превью Android Compose-контейнера. */
@Preview
@Composable
private fun AppPreview() {
    App()
}
