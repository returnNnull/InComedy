package com.bam.incomedy.feature.notifications.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NotificationsTabContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    @Test
    fun showsFeedAndAutoLoadsEligibleEvent() {
        var loadedEventId: String? = null

        composeRule.setContent {
            MaterialTheme {
                AnnouncementFeedTab(
                    notificationsBindings = NotificationsTabBindings(
                        state = AndroidUiStateFactory.notificationsState(),
                        organizerEvents = listOf(
                            AndroidUiStateFactory.event(
                                id = "event-draft",
                                status = EventStatus.DRAFT,
                            ),
                            AndroidUiStateFactory.event(
                                id = "event-2",
                                title = "Published Feed",
                                status = EventStatus.PUBLISHED,
                                visibility = EventVisibility.PUBLIC,
                            ),
                        ),
                        onLoadAnnouncements = { loadedEventId = it },
                    ),
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AnnouncementScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AnnouncementScreenTags.COUNT).assertTextEquals("Анонсов: 2")
        composeRule.onNodeWithTag("${AnnouncementScreenTags.EVENT_SELECTOR_PREFIX}event-2").assertIsDisplayed()
        composeRule.onNodeWithTag(AnnouncementScreenTags.PUBLISH_MESSAGE_INPUT)
            .performTextReplacement("Старт через 5 минут")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AnnouncementScreenTags.PUBLISH_BUTTON)
            .assertIsEnabled()

        assertEquals("event-2", loadedEventId)
    }

    @Test
    fun showsLockedStateWhenNoPublishedPublicEventsExist() {
        composeRule.setContent {
            MaterialTheme {
                AnnouncementFeedTab(
                    notificationsBindings = NotificationsTabBindings(
                        state = AndroidUiStateFactory.notificationsState(announcements = emptyList()),
                        organizerEvents = listOf(
                            AndroidUiStateFactory.event(
                                id = "event-1",
                                status = EventStatus.DRAFT,
                            ),
                            AndroidUiStateFactory.event(
                                id = "event-2",
                                status = EventStatus.PUBLISHED,
                                visibility = EventVisibility.PRIVATE,
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(AnnouncementScreenTags.EVENT_EMPTY_STATE).assertIsDisplayed()
    }
}
