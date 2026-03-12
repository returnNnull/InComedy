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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

/**
 * Android-экран авторизации, который связывает UI с `AuthAndroidViewModel`.
 *
 * @property viewModel Android-адаптер авторизации, поставляющий состояние и эффекты.
 * @property modifier Внешний модификатор контейнера экрана.
 */
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

    AuthScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * Тестируемое содержимое экрана авторизации без прямой зависимости от Android `ViewModel`.
 *
 * @property state Текущее состояние авторизации.
 * @property onIntent Обработчик пользовательских намерений экрана.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun AuthScreenContent(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .testTag(AuthScreenTags.ROOT),
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
                modifier = Modifier.testTag(AuthScreenTags.ERROR_MESSAGE),
            )
        }

        if (state.isAuthorized) {
            Text(
                text = "Успешная авторизация через ${state.session?.provider}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(AuthScreenTags.AUTHORIZED_STATE),
            )
            return@Column
        }

        AuthButton(
            title = "Войти через VK",
            testTag = AuthScreenTags.BUTTON_VK,
            isLoading = state.isLoading,
            onClick = { onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK)) },
        )
        AuthButton(
            title = "Войти через Telegram",
            testTag = AuthScreenTags.BUTTON_TELEGRAM,
            isLoading = state.isLoading,
            onClick = { onIntent(AuthIntent.OnProviderClick(AuthProviderType.TELEGRAM)) },
        )
        AuthButton(
            title = "Войти через Google",
            testTag = AuthScreenTags.BUTTON_GOOGLE,
            isLoading = state.isLoading,
            onClick = { onIntent(AuthIntent.OnProviderClick(AuthProviderType.GOOGLE)) },
        )
    }
}

/**
 * Кнопка запуска входа через конкретного провайдера.
 *
 * @property title Подпись кнопки.
 * @property testTag Стабильный UI-тег кнопки.
 * @property isLoading Признак того, что вход уже запущен и кнопку нужно заблокировать.
 * @property onClick Обработчик нажатия на кнопку.
 */
@Composable
private fun AuthButton(
    title: String,
    testTag: String,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        enabled = !isLoading,
        onClick = onClick,
    ) {
        val suffix = if (isLoading) "..." else ""
        Text("$title$suffix")
    }
}

/**
 * Стабильные теги UI-элементов экрана авторизации для Android-тестов.
 */
object AuthScreenTags {
    /** Тег корневого контейнера экрана. */
    const val ROOT = "auth.root"

    /** Тег строки ошибки авторизации. */
    const val ERROR_MESSAGE = "auth.error"

    /** Тег успешного авторизованного состояния. */
    const val AUTHORIZED_STATE = "auth.authorized"

    /** Тег кнопки входа через VK. */
    const val BUTTON_VK = "auth.button.vk"

    /** Тег кнопки входа через Telegram. */
    const val BUTTON_TELEGRAM = "auth.button.telegram"

    /** Тег кнопки входа через Google. */
    const val BUTTON_GOOGLE = "auth.button.google"
}
