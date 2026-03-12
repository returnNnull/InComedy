package com.bam.incomedy.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android UI-тесты корневой навигации между auth- и main-графами.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppNavHostContentTest {

    /** Правило Compose, которое поднимает test Activity внутри JVM-теста. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет, что неавторизованное состояние оставляет пользователя в auth-графе. */
    @Test
    fun unauthorizedStateShowsAuthGraph() {
        setAppNavHostContent(isAuthorized = false)

        composeRule.onNodeWithTag(AppNavHostTestTags.AUTH_SCREEN).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AppNavHostTestTags.MAIN_SCREEN).assertCountEquals(0)
    }

    /** Проверяет, что авторизованное состояние переключает корневую навигацию в main-граф. */
    @Test
    fun authorizedStateShowsMainGraph() {
        setAppNavHostContent(isAuthorized = true)

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppNavHostTestTags.MAIN_SCREEN).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AppNavHostTestTags.AUTH_SCREEN).assertCountEquals(0)
    }

    /** Проверяет возврат в auth-граф после сброса авторизованного состояния, например после выхода. */
    @Test
    fun stateResetReturnsUserToAuthGraph() {
        val isAuthorized = mutableStateOf(true)

        composeRule.setContent {
            MaterialTheme {
                AppNavHostContent(
                    isAuthorized = isAuthorized.value,
                    authContent = { modifier ->
                        PlaceholderScreen(
                            modifier = modifier,
                            testTag = AppNavHostTestTags.AUTH_SCREEN,
                        )
                    },
                    mainContent = { modifier ->
                        PlaceholderScreen(
                            modifier = modifier,
                            testTag = AppNavHostTestTags.MAIN_SCREEN,
                        )
                    },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppNavHostTestTags.MAIN_SCREEN).assertIsDisplayed()

        isAuthorized.value = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AppNavHostTestTags.AUTH_SCREEN).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AppNavHostTestTags.MAIN_SCREEN).assertCountEquals(0)
    }

    /** Поднимает тестируемое содержимое корневой навигации с плейсхолдерами экранов. */
    private fun setAppNavHostContent(
        isAuthorized: Boolean,
    ) {
        composeRule.setContent {
            MaterialTheme {
                AppNavHostContent(
                    isAuthorized = isAuthorized,
                    authContent = { modifier ->
                        PlaceholderScreen(
                            modifier = modifier,
                            testTag = AppNavHostTestTags.AUTH_SCREEN,
                        )
                    },
                    mainContent = { modifier ->
                        PlaceholderScreen(
                            modifier = modifier,
                            testTag = AppNavHostTestTags.MAIN_SCREEN,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Тестовый плейсхолдер экрана для проверки корневой навигации без реальных экранов.
 *
 * @property modifier Внешний модификатор, который приходит из `NavHost`.
 * @property testTag UI-тег конкретного плейсхолдера.
 */
@androidx.compose.runtime.Composable
private fun PlaceholderScreen(
    modifier: Modifier,
    testTag: String,
) {
    Box(
        modifier = modifier.testTag(testTag),
    )
}

/**
 * Набор тестовых тегов для проверки корневой Android-навигации.
 */
private object AppNavHostTestTags {
    /** Тег auth-плейсхолдера. */
    const val AUTH_SCREEN = "appNav.auth"

    /** Тег main-плейсхолдера. */
    const val MAIN_SCREEN = "appNav.main"
}
