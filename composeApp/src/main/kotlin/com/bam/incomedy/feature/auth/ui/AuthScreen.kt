package com.bam.incomedy.feature.auth.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthFlowLogger
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

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

private fun openExternalAuth(
    context: Context,
    effect: AuthEffect.OpenExternalAuth,
) {
    val uri = Uri.parse(effect.url)
    val launchPlan = AndroidExternalAuthIntents.plan(
        effect = effect,
        uri = uri,
    ) { intent ->
        intent.resolveActivity(context.packageManager) != null
    }
    val urlSummary = uri.safeSummary()
    val intentSummary = launchPlan.primaryIntent.safeSummary()
    AuthFlowLogger.event(
        stage = "android.external_auth.intent_prepared",
        provider = effect.provider,
        details = "url=$urlSummary intent=$intentSummary launchMode=${launchPlan.launchMode}",
    )

    runCatching {
        context.startActivity(launchPlan.primaryIntent)
    }.onSuccess {
        AuthFlowLogger.event(
            stage = "android.external_auth.intent_started",
            provider = effect.provider,
            details = "intent=$intentSummary launchMode=${launchPlan.launchMode}",
        )
    }.onFailure { error ->
        val fallbackIntent = launchPlan.fallbackIntent
        if (fallbackIntent == null) {
            AuthFlowLogger.event(
                stage = "android.external_auth.intent_failed",
                provider = effect.provider,
                details = "reason=${error.message ?: "unknown"} intent=$intentSummary launchMode=${launchPlan.launchMode}",
            )
            return@onFailure
        }

        val fallbackSummary = fallbackIntent.safeSummary()
        AuthFlowLogger.event(
            stage = "android.external_auth.intent_failed",
            provider = effect.provider,
            details = "reason=${error.message ?: "unknown"} intent=$intentSummary launchMode=${launchPlan.launchMode} fallbackIntent=$fallbackSummary",
        )
        runCatching {
            context.startActivity(fallbackIntent)
        }.onSuccess {
            AuthFlowLogger.event(
                stage = "android.external_auth.browser_fallback_started",
                provider = effect.provider,
                details = "intent=$fallbackSummary",
            )
        }.onFailure { fallbackError ->
            AuthFlowLogger.event(
                stage = "android.external_auth.browser_fallback_failed",
                provider = effect.provider,
                details = "reason=${fallbackError.message ?: "unknown"} intent=$fallbackSummary",
            )
        }
    }
}

private fun Intent.safeSummary(): String {
    val uri = data
    val keys = uri?.queryParameterNames?.sorted()?.joinToString(",").orEmpty()
    return buildString {
        append("package=")
        append(`package` ?: "n/a")
        append("|scheme=")
        append(uri?.scheme ?: "n/a")
        append("|host=")
        append(uri?.host ?: "n/a")
        append("|path=")
        append(uri?.path ?: "/")
        append("|keys=")
        append(if (keys.isBlank()) "none" else keys)
        append("|len=")
        append(uri?.toString()?.length ?: 0)
    }
}

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

@Composable
internal fun AuthScreenContent(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }

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

        if (state.isAuthorized) {
            Text(
                text = "Успешная авторизация через ${providerTitle(state.session?.provider)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(AuthScreenTags.AUTHORIZED_STATE),
            )
            return@Column
        }

        Text(
            text = "Основной вход: логин и пароль. VK доступен как внешний провайдер.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(AuthScreenTags.AUTH_STANDARD),
        )

        Text(
            text = if (isRegisterMode) "Создайте аккаунт, затем при желании подключите VK." else "Войдите по логину и паролю или используйте VK.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(AuthScreenTags.NEXT_STEP),
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AuthScreenTags.ERROR_MESSAGE),
            )
        }

        TextButton(
            onClick = { isRegisterMode = !isRegisterMode },
            modifier = Modifier.testTag(AuthScreenTags.MODE_SWITCH),
        ) {
            Text(if (isRegisterMode) "У меня уже есть аккаунт" else "Создать аккаунт")
        }

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Логин") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthScreenTags.LOGIN_FIELD),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthScreenTags.PASSWORD_FIELD),
        )

        Button(
            onClick = {
                if (isRegisterMode) {
                    onIntent(AuthIntent.OnRegisterSubmit(login = login, password = password))
                } else {
                    onIntent(AuthIntent.OnSignInSubmit(login = login, password = password))
                }
            },
            enabled = !state.isLoading && login.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthScreenTags.SUBMIT_BUTTON),
        ) {
            Text(if (isRegisterMode) "Создать аккаунт" else "Войти")
        }

        Divider()

        Button(
            onClick = { onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK)) },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AuthScreenTags.VK_BUTTON),
        ) {
            Text("Продолжить через VK")
        }
    }
}

private fun providerTitle(provider: AuthProviderType?): String {
    return when (provider) {
        AuthProviderType.PASSWORD -> "логин и пароль"
        AuthProviderType.PHONE -> "телефон"
        AuthProviderType.VK -> "VK"
        AuthProviderType.TELEGRAM -> "Telegram"
        AuthProviderType.GOOGLE -> "Google"
        null -> "неизвестный способ"
    }
}

object AuthScreenTags {
    const val ROOT = "auth.root"
    const val ERROR_MESSAGE = "auth.error"
    const val AUTHORIZED_STATE = "auth.authorized"
    const val AUTH_STANDARD = "auth.standard"
    const val NEXT_STEP = "auth.next_step"
    const val MODE_SWITCH = "auth.modeSwitch"
    const val LOGIN_FIELD = "auth.login"
    const val PASSWORD_FIELD = "auth.password"
    const val SUBMIT_BUTTON = "auth.submit"
    const val VK_BUTTON = "auth.vk"
}
