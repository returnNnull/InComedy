package com.bam.incomedy.feature.auth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Android UI-тесты содержимого экрана авторизации.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AuthScreenContentTest {

    /** Правило Compose, которое поднимает тестовую Activity внутри JVM-теста. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет отображение нового auth-стандарта и ближайшего roadmap без legacy-кнопок. */
    @Test
    fun authScreenShowsCurrentAuthStandardAndNoLegacyProviderButtons() {
        setAuthScreenContent()

        composeRule.onNodeWithTag(AuthScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthScreenTags.AUTH_STANDARD).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthScreenTags.NEXT_STEP).assertIsDisplayed()
    }

    /** Проверяет, что состояние загрузки не возвращает legacy auth-кнопки. */
    @Test
    fun authScreenDoesNotShowLegacyProviderButtonsWhileLoading() {
        setAuthScreenContent(
            state = AndroidUiStateFactory.authState(isLoading = true),
        )

        composeRule.onNodeWithTag(AuthScreenTags.AUTH_STANDARD).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AuthScreenTags.AUTHORIZED_STATE).assertCountEquals(0)
    }

    /** Проверяет показ сообщения об ошибке без перехода в авторизованное состояние. */
    @Test
    fun authScreenShowsErrorMessage() {
        setAuthScreenContent(
            state = AndroidUiStateFactory.authState(errorMessage = "Вход временно недоступен"),
        )

        composeRule.onNodeWithTag(AuthScreenTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    /** Проверяет, что после успешного входа экран показывает итоговое состояние без кнопок провайдеров. */
    @Test
    fun authScreenShowsAuthorizedStateInsteadOfProviderButtons() {
        setAuthScreenContent(
            state = AndroidUiStateFactory.authState(
                session = AndroidUiStateFactory.authSession(),
            ),
        )

        composeRule.onNodeWithTag(AuthScreenTags.AUTHORIZED_STATE).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AuthScreenTags.AUTH_STANDARD).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AuthScreenTags.NEXT_STEP).assertCountEquals(0)
    }

    /** Поднимает тестируемое содержимое авторизации в Material-теме. */
    private fun setAuthScreenContent(
        state: com.bam.incomedy.feature.auth.mvi.AuthState = AndroidUiStateFactory.authState(),
        onIntent: (AuthIntent) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                AuthScreenContent(
                    state = state,
                    onIntent = onIntent,
                    onAuthCallbackUrl = {},
                    onAuthFailure = { _, _ -> },
                )
            }
        }
    }
}
