package com.bam.incomedy.feature.notifications.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AnnouncementFeedTabContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    @Test
    fun showsAnnouncementFeedAndPublishControls() {
        composeRule.setContent {
            MaterialTheme {
                AnnouncementFeedTab(
                    notificationsBindings = NotificationsTabBindings(
                        state = AndroidUiStateFactory.notificationsState(
                            selectedEventId = "event-2",
                            announcements = listOf(
                                AndroidUiStateFactory.announcement(
                                    id = "announcement-1",
                                    eventId = "event-2",
                                ),
                                AndroidUiStateFactory.announcement(
                                    id = "announcement-2",
                                    eventId = "event-2",
                                    message = "Сет задерживается на 5 минут",
                                    authorRole = EventAnnouncementAuthorRole.HOST,
                                    createdAtIso = "2026-03-27T19:05:00+03:00",
                                ),
                            ),
                        ),
                        organizerEvents = listOf(
                            AndroidUiStateFactory.event(
                                id = "event-2",
                                status = EventStatus.PUBLISHED,
                                visibility = EventVisibility.PUBLIC,
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AnnouncementScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AnnouncementScreenTags.COUNT).assertTextEquals("Анонсов: 2")
        composeRule.onNodeWithTag("${AnnouncementScreenTags.EVENT_SELECTOR_PREFIX}event-2").performClick()
        composeRule.onNodeWithTag(AnnouncementScreenTags.PUBLISH_MESSAGE_INPUT).performTextReplacement("Новый анонс")
        composeRule.onNodeWithTag(AnnouncementScreenTags.PUBLISH_BUTTON).assertIsEnabled()
    }

    @Test
    fun showsEmptyEventStateWhenNoEligibleEvents() {
        composeRule.setContent {
            MaterialTheme {
                AnnouncementFeedTab(
                    notificationsBindings = NotificationsTabBindings(
                        state = AndroidUiStateFactory.notificationsState(
                            announcements = emptyList(),
                        ),
                        organizerEvents = emptyList(),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(AnnouncementScreenTags.EVENT_EMPTY_STATE).assertIsDisplayed()
    }
}
