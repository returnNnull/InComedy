package com.bam.incomedy.feature.auth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.bam.incomedy.feature.auth.domain.AuthProviderType
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

    /** Проверяет отображение кнопок провайдеров и корректную отправку интентов на нажатие. */
    @Test
    fun authScreenShowsProviderButtonsAndDispatchesProviderIntents() {
        val intents = mutableListOf<AuthIntent>()

        setAuthScreenContent(
            onIntent = { intents += it },
        )

        composeRule.onNodeWithTag(AuthScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_VK).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_TELEGRAM).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_GOOGLE).assertIsDisplayed().performClick()

        assertEquals(
            listOf<AuthIntent>(
                AuthIntent.OnProviderClick(AuthProviderType.VK),
                AuthIntent.OnProviderClick(AuthProviderType.TELEGRAM),
                AuthIntent.OnProviderClick(AuthProviderType.GOOGLE),
            ),
            intents,
        )
    }

    /** Проверяет, что во время загрузки все кнопки входа блокируются. */
    @Test
    fun authScreenDisablesProviderButtonsWhileLoading() {
        setAuthScreenContent(
            state = AndroidUiStateFactory.authState(isLoading = true),
        )

        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_VK).assertIsNotEnabled()
        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_TELEGRAM).assertIsNotEnabled()
        composeRule.onNodeWithTag(AuthScreenTags.BUTTON_GOOGLE).assertIsNotEnabled()
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
        composeRule.onAllNodesWithTag(AuthScreenTags.BUTTON_VK).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AuthScreenTags.BUTTON_TELEGRAM).assertCountEquals(0)
        composeRule.onAllNodesWithTag(AuthScreenTags.BUTTON_GOOGLE).assertCountEquals(0)
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
                )
            }
        }
    }
}
