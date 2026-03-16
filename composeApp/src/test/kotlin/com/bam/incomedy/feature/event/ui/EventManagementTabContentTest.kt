package com.bam.incomedy.feature.event.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * UI-тесты organizer event management tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class EventManagementTabContentTest {
    /** Правило Compose, которое поднимает экран внутри JVM-теста. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет, что форма создания события прокидывает выбранные ids и поля. */
    @Test
    fun createEventFormInvokesCallbackWithSelectedWorkspaceVenueAndTemplate() {
        var capturedForm: EventCreateForm? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(),
                            onCreateEvent = { form -> capturedForm = form },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.WORKSPACE_SELECTOR_PREFIX}ws-1").performClick()
        composeRule.onNodeWithTag("${EventScreenTags.VENUE_SELECTOR_PREFIX}venue-1").performClick()
        composeRule.onNodeWithTag("${EventScreenTags.TEMPLATE_SELECTOR_PREFIX}template-1").performClick()
        composeRule.onNodeWithTag(EventScreenTags.TITLE_INPUT).performTextInput("Late Night Standup")
        composeRule.onNodeWithTag(EventScreenTags.CREATE_BUTTON).performScrollTo().performClick()

        val form = requireNotNull(capturedForm)
        assertEquals("ws-1", form.workspaceId)
        assertEquals("venue-1", form.venueId)
        assertEquals("template-1", form.hallTemplateId)
        assertEquals("Late Night Standup", form.title)
    }

    /** Проверяет, что draft-событие показывает publish action и вызывает callback. */
    @Test
    fun draftEventCardInvokesPublishCallback() {
        var publishedEventId: String? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(),
                            onPublishEvent = { eventId -> publishedEventId = eventId },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.PUBLISH_BUTTON_PREFIX}event-1").performScrollTo().performClick()

        assertEquals("event-1", publishedEventId)
    }
}
