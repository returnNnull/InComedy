package com.bam.incomedy.feature.auth.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
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
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AuthEffect.OpenExternalAuth -> openExternalAuth(context = context, effect = effect)
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
 * Открывает внешний auth URL через явный Android browser intent и пишет безопасный summary URL.
 *
 * Compose `LocalUriHandler` скрывает детали `Intent`, поэтому для отладки Telegram launch path
 * здесь используется прямой `ACTION_VIEW`, который позволяет сверить состав query-параметров
 * до фактического перехода в браузер.
 *
 * @property context Android context, из которого запускается браузер.
 * @property effect Эффект с провайдером и auth URL.
 */
private fun openExternalAuth(
    context: Context,
    effect: AuthEffect.OpenExternalAuth,
) {
    val uri = Uri.parse(effect.url)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val urlSummary = uri.safeSummary()
    val intentSummary = intent.data.safeSummary()
    AuthFlowLogger.event(
        stage = "android.external_auth.intent_prepared",
        provider = effect.provider,
        details = "url=$urlSummary intentData=$intentSummary",
    )

    runCatching {
        context.startActivity(intent)
    }.onSuccess {
        AuthFlowLogger.event(
            stage = "android.external_auth.intent_started",
            provider = effect.provider,
            details = "intentData=$intentSummary",
        )
    }.onFailure { error ->
        AuthFlowLogger.event(
            stage = "android.external_auth.intent_failed",
            provider = effect.provider,
            details = "reason=${error.message ?: "unknown"} intentData=$intentSummary",
        )
    }
}

/**
 * Возвращает безопасное summary URI без чувствительных значений query-параметров.
 *
 * Summary нужен для отладки потери параметров при браузерном запуске, но не должен утекать
 * в логи как полный callback/auth URL.
 */
private fun Uri?.safeSummary(): String {
    if (this == null) return "null"
    val keys = queryParameterNames.sorted().joinToString(",")
    return buildString {
        append("scheme=")
        append(scheme ?: "n/a")
        append("|host=")
        append(host ?: "n/a")
        append("|path=")
        append(path ?: "/")
        append("|keys=")
        append(if (keys.isBlank()) "none" else keys)
        append("|len=")
        append(toString().length)
    }
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
