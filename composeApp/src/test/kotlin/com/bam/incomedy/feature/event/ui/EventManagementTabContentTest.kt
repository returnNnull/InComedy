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
import androidx.compose.ui.test.performTextReplacement
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
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

    /** Проверяет, что published-событие с закрытыми продажами вызывает open-sales callback. */
    @Test
    fun publishedEventCardInvokesOpenSalesCallback() {
        var openedEventId: String? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(
                                events = listOf(
                                    AndroidUiStateFactory.event(
                                        status = EventStatus.PUBLISHED,
                                        salesStatus = EventSalesStatus.CLOSED,
                                    ),
                                ),
                            ),
                            onOpenEventSales = { eventId -> openedEventId = eventId },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.OPEN_SALES_BUTTON_PREFIX}event-1")
            .performScrollTo()
            .performClick()

        assertEquals("event-1", openedEventId)
    }

    /** Проверяет, что on-sale событие вызывает pause-sales callback. */
    @Test
    fun onSaleEventCardInvokesPauseSalesCallback() {
        var pausedEventId: String? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(
                                events = listOf(
                                    AndroidUiStateFactory.event(
                                        status = EventStatus.PUBLISHED,
                                        salesStatus = EventSalesStatus.OPEN,
                                    ),
                                ),
                            ),
                            onPauseEventSales = { eventId -> pausedEventId = eventId },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.PAUSE_SALES_BUTTON_PREFIX}event-1")
            .performScrollTo()
            .performClick()

        assertEquals("event-1", pausedEventId)
    }

    /** Проверяет, что опубликованное событие вызывает cancel callback. */
    @Test
    fun publishedEventCardInvokesCancelCallback() {
        var canceledEventId: String? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(
                                events = listOf(
                                    AndroidUiStateFactory.event(
                                        status = EventStatus.PUBLISHED,
                                        salesStatus = EventSalesStatus.OPEN,
                                    ),
                                ),
                            ),
                            onCancelEvent = { eventId -> canceledEventId = eventId },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.CANCEL_BUTTON_PREFIX}event-1")
            .performScrollTo()
            .performClick()

        assertEquals("event-1", canceledEventId)
    }

    /** Проверяет, что override editor прокидывает update form выбранного события. */
    @Test
    fun eventOverrideEditorInvokesUpdateCallback() {
        var capturedForm: EventUpdateForm? = null

        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    EventManagementTab(
                        workspaces = listOf(AndroidUiStateFactory.workspace()),
                        eventBindings = EventTabBindings(
                            state = AndroidUiStateFactory.eventState(),
                            onUpdateEvent = { form -> capturedForm = form },
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${EventScreenTags.EDIT_BUTTON_PREFIX}event-1").performScrollTo().performClick()
        composeRule.onNodeWithTag(EventScreenTags.UPDATE_TITLE_INPUT).performScrollTo().performTextReplacement("Updated Event")
        composeRule.onNodeWithTag(EventScreenTags.UPDATE_PRICE_ZONES_INPUT)
            .performScrollTo()
            .performTextReplacement("event-vip|VIP|3500|RUB")
        composeRule.onNodeWithTag(EventScreenTags.UPDATE_PRICING_ASSIGNMENTS_INPUT)
            .performScrollTo()
            .performTextReplacement("row|row-a|event-vip")
        composeRule.onNodeWithTag(EventScreenTags.UPDATE_AVAILABILITY_OVERRIDES_INPUT)
            .performScrollTo()
            .performTextReplacement("seat|row-a-2|blocked")
        composeRule.onNodeWithTag(EventScreenTags.UPDATE_SAVE_BUTTON).performScrollTo().performClick()

        val form = requireNotNull(capturedForm)
        assertEquals("event-1", form.eventId)
        assertEquals("Updated Event", form.title)
        assertEquals("event-vip|VIP|3500|RUB", form.priceZonesText)
        assertEquals("row|row-a|event-vip", form.pricingAssignmentsText)
        assertEquals("seat|row-a-2|blocked", form.availabilityOverridesText)
    }
}
