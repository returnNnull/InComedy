package com.bam.incomedy.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

@Composable
fun AuthScreen(
    viewModel: AuthAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AuthEffect.OpenExternalAuth -> uriHandler.openUri(effect.url)
                AuthEffect.InvalidateStoredSession -> Unit
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Авторизация",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isAuthorized) {
            Text(
                text = "Успешная авторизация через ${state.session?.provider}",
                style = MaterialTheme.typography.bodyLarge,
            )
            return@Column
        }

        AuthButton(
            title = "Войти через VK",
            isLoading = state.isLoading,
            onClick = { viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK)) },
        )
        AuthButton(
            title = "Войти через Telegram",
            isLoading = state.isLoading,
            onClick = { viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.TELEGRAM)) },
        )
        AuthButton(
            title = "Войти через Google",
            isLoading = state.isLoading,
            onClick = { viewModel.onIntent(AuthIntent.OnProviderClick(AuthProviderType.GOOGLE)) },
        )

    }
}

@Composable
private fun AuthButton(
    title: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        onClick = onClick,
    ) {
        val suffix = if (isLoading) "..." else ""
        Text("$title$suffix")
    }
}
