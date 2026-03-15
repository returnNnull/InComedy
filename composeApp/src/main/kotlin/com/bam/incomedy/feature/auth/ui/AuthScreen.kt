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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vk.id.auth.VKIDAuthUiParams
import com.vk.id.onetap.compose.onetap.OneTap

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
                is AuthEffect.OpenExternalAuth -> openExternalAuth(
                    context = context,
                    effect = effect,
                )
                AuthEffect.InvalidateStoredSession -> Unit
            }
        }
    }

    AuthScreenContent(
        state = state,
        onIntent = viewModel::onIntent,
        onAuthCallbackUrl = viewModel::onAuthCallbackUrl,
        onAuthFailure = { provider, message ->
            viewModel.onIntent(
                AuthIntent.OnAuthFailure(
                    provider = provider,
                    message = message,
                ),
            )
        },
        modifier = modifier,
    )
}

/** Открывает внешний browser/public-callback auth-flow через системные intent-ы Android. */
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
    onAuthCallbackUrl: (String?) -> Unit,
    onAuthFailure: (AuthProviderType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

        HorizontalDivider()

        VkAuthEntry(
            state = state,
            onIntent = onIntent,
            onAuthCallbackUrl = onAuthCallbackUrl,
            onAuthFailure = onAuthFailure,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Рендерит Android VK OneTap по documented SDK lifecycle и держит browser fallback под рукой. */
@Composable
private fun VkAuthEntry(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    onAuthCallbackUrl: (String?) -> Unit,
    onAuthFailure: (AuthProviderType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtimeConfig = AndroidVkIdSdkAuth.runtimeConfig()
    val vkAttemptState = rememberVkAuthAttemptState()
    val authAttempt = vkAttemptState.current

    LaunchedEffect(runtimeConfig.isEnabled, runtimeConfig.clientId, runtimeConfig.redirectScheme) {
        if (runtimeConfig.isEnabled && !runtimeConfig.canUseOneTap()) {
            AuthFlowLogger.event(
                stage = "android.vk_onetap.fallback",
                provider = AuthProviderType.VK,
                details = "reason=${runtimeConfig.unavailableReason()}",
            )
        }
    }
    LaunchedEffect(runtimeConfig.canUseOneTap()) {
        if (runtimeConfig.canUseOneTap()) {
            AuthFlowLogger.event(
                stage = "android.vk_onetap.ready",
                provider = AuthProviderType.VK,
                details = "scopes=${runtimeConfig.requestedScopes.size}",
            )
        }
    }

    if (runtimeConfig.canUseOneTap()) {
        val authParams = VKIDAuthUiParams {
            this.state = authAttempt.state
            codeChallenge = authAttempt.codeChallenge
            scopes = runtimeConfig.requestedScopes
        }
        OneTap(
            modifier = modifier.testTag(AuthScreenTags.VK_BUTTON),
            onAuth = { _, accessToken ->
                AuthFlowLogger.event(
                    stage = "android.vk_onetap.unexpected_access_token",
                    provider = AuthProviderType.VK,
                    details = "tokenPresent=${accessToken.token.isNotBlank()}",
                )
                onAuthFailure(AuthProviderType.VK, "VK OneTap did not return an auth code")
                vkAttemptState.rotate()
            },
            onAuthCode = { data, isCompletion ->
                AuthFlowLogger.event(
                    stage = "android.vk_onetap.code_received",
                    provider = AuthProviderType.VK,
                    details = "deviceIdPresent=${data.deviceId.isNotBlank()} completion=$isCompletion",
                )
                onAuthCallbackUrl(
                    buildVkSdkCallbackUrl(
                        code = data.code,
                        state = authAttempt.state,
                        deviceId = data.deviceId,
                        codeVerifier = authAttempt.codeVerifier,
                    ),
                )
                vkAttemptState.rotate()
            },
            onFail = { _, fail ->
                val reason = fail.safeReason()
                AuthFlowLogger.event(
                    stage = "android.vk_onetap.failed",
                    provider = AuthProviderType.VK,
                    details = "reason=$reason",
                )
                onAuthFailure(AuthProviderType.VK, "VK auth failed: $reason")
                vkAttemptState.rotate()
            },
            signInAnotherAccountButtonEnabled = true,
            authParams = authParams,
        )
        TextButton(
            onClick = {
                AuthFlowLogger.event(
                    stage = "android.vk_onetap.browser_fallback_requested",
                    provider = AuthProviderType.VK,
                )
                onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK))
            },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Открыть VK в браузере")
        }
        return
    }

    Button(
        onClick = { onIntent(AuthIntent.OnProviderClick(AuthProviderType.VK)) },
        enabled = !state.isLoading,
        modifier = modifier.testTag(AuthScreenTags.VK_BUTTON),
    ) {
        Text("Продолжить через VK")
    }
}

/**
 * Хранит текущую локальную VK auth-попытку и умеет атомарно перевыпускать ее после успеха/ошибки.
 *
 * Такой state живет на Android стороне, потому что documented VK OneTap flow требует client-owned
 * `state` и PKCE, а backend получает их только на этапе verify.
 */
@Composable
private fun rememberVkAuthAttemptState(): VkAuthAttemptState {
    val initialAttempt = remember { AndroidVkIdSdkAuth.newAuthAttempt() }
    var attemptState by rememberSaveable { mutableStateOf(initialAttempt.state) }
    var codeVerifier by rememberSaveable { mutableStateOf(initialAttempt.codeVerifier) }
    var codeChallenge by rememberSaveable { mutableStateOf(initialAttempt.codeChallenge) }
    val rotate: () -> Unit = {
        val nextAttempt = AndroidVkIdSdkAuth.newAuthAttempt()
        attemptState = nextAttempt.state
        codeVerifier = nextAttempt.codeVerifier
        codeChallenge = nextAttempt.codeChallenge
    }
    return remember(attemptState, codeVerifier, codeChallenge) {
        VkAuthAttemptState(
            current = AndroidVkIdAuthAttempt(
                state = attemptState,
                codeVerifier = codeVerifier,
                codeChallenge = codeChallenge,
            ),
            rotate = rotate,
        )
    }
}

/** Компактный holder для текущей VK auth-попытки и операции перевыпуска. */
private data class VkAuthAttemptState(
    val current: AndroidVkIdAuthAttempt,
    val rotate: () -> Unit,
)

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
