package com.bam.incomedy.feature.lineup.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Android UI-тесты вкладки comedian applications и organizer lineup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LineupManagementTabContentTest {

    /** Правило Compose, которое поднимает экран внутри JVM-теста. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет выбор organizer event и вызов загрузки context-а. */
    @Test
    fun organizerEventSelectionLoadsContext() {
        var loadedEventId: String? = null

        setLineupContent(
            bindings = LineupTabBindings(
                state = AndroidUiStateFactory.lineupState(),
                organizerEvents = AndroidUiStateFactory.eventState().events,
                onLoadOrganizerContext = { loadedEventId = it },
            ),
        )

        composeRule.onNodeWithTag(LineupScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(LineupScreenTags.LOAD_BUTTON).performClick()

        assertEquals("event-1", loadedEventId)
    }

    /** Проверяет, что submit form отправляет event id и заметку. */
    @Test
    fun submitFormInvokesApplicationCallback() {
        var submittedEventId: String? = null
        var submittedNote: String? = null

        setLineupContent(
            bindings = LineupTabBindings(
                state = AndroidUiStateFactory.lineupState(applications = emptyList(), lineup = emptyList()),
                organizerEvents = emptyList(),
                onSubmitApplication = { eventId, note ->
                    submittedEventId = eventId
                    submittedNote = note
                },
            ),
        )

        composeRule.onNodeWithTag(LineupScreenTags.SUBMIT_EVENT_ID_INPUT)
            .performTextInput("event-manual")
        composeRule.onNodeWithTag(LineupScreenTags.SUBMIT_NOTE_INPUT)
            .performTextInput("Несу новый сет")
        composeRule.onNodeWithTag(LineupScreenTags.SUBMIT_BUTTON)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("event-manual", submittedEventId)
        assertEquals("Несу новый сет", submittedNote)
    }

    /** Проверяет, что organizer review action прокидывает нужный статус. */
    @Test
    fun applicationActionInvokesStatusUpdate() {
        var updatedApplicationId: String? = null
        var updatedEventId: String? = null
        var updatedStatus: ComedianApplicationStatus? = null

        setLineupContent(
            bindings = LineupTabBindings(
                state = AndroidUiStateFactory.lineupState(),
                organizerEvents = AndroidUiStateFactory.eventState().events,
                onUpdateApplicationStatus = { eventId, applicationId, status ->
                    updatedEventId = eventId
                    updatedApplicationId = applicationId
                    updatedStatus = status
                },
            ),
        )

        composeRule.onNodeWithTag("lineup.application.action.application-1.approved")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("event-1", updatedEventId)
        assertEquals("application-1", updatedApplicationId)
        assertEquals(ComedianApplicationStatus.APPROVED, updatedStatus)
    }

    /** Проверяет, что кнопка смещения вниз пересобирает полный порядок lineup ids. */
    @Test
    fun moveDownInvokesReorderWithUpdatedOrder() {
        var reorderedEventId: String? = null
        var reorderedIds: List<String>? = null

        setLineupContent(
            bindings = LineupTabBindings(
                state = AndroidUiStateFactory.lineupState(
                    lineup = listOf(
                        AndroidUiStateFactory.lineupEntry(id = "entry-1", orderIndex = 1),
                        AndroidUiStateFactory.lineupEntry(id = "entry-2", orderIndex = 2),
                    ),
                ),
                organizerEvents = AndroidUiStateFactory.eventState().events,
                onReorderLineup = { eventId, orderedIds ->
                    reorderedEventId = eventId
                    reorderedIds = orderedIds
                },
            ),
        )

        composeRule.onNodeWithTag("lineup.entry.moveDown.entry-1")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("event-1", reorderedEventId)
        assertEquals(listOf("entry-2", "entry-1"), reorderedIds)
    }

    /** Проверяет summary current/next performer и organizer live control callback. */
    @Test
    fun liveStageSummaryAndActionsUseSharedStatusContract() {
        var updatedEventId: String? = null
        var updatedEntryId: String? = null
        var updatedStatus: LineupEntryStatus? = null

        setLineupContent(
            bindings = LineupTabBindings(
                state = AndroidUiStateFactory.lineupState(
                    lineup = listOf(
                        AndroidUiStateFactory.lineupEntry(
                            id = "entry-1",
                            orderIndex = 1,
                            status = LineupEntryStatus.ON_STAGE,
                            comedianDisplayName = "Иван Смехов",
                        ),
                        AndroidUiStateFactory.lineupEntry(
                            id = "entry-2",
                            orderIndex = 2,
                            status = LineupEntryStatus.UP_NEXT,
                            comedianDisplayName = "Мария Сетова",
                        ),
                    ),
                ),
                organizerEvents = AndroidUiStateFactory.eventState().events,
                onUpdateLineupEntryStatus = { eventId, entryId, status ->
                    updatedEventId = eventId
                    updatedEntryId = entryId
                    updatedStatus = status
                },
            ),
        )

        composeRule.onNodeWithTag(LineupScreenTags.CURRENT_PERFORMER)
            .assertTextEquals("Сейчас на сцене: Иван Смехов")
        composeRule.onNodeWithTag(LineupScreenTags.NEXT_UP_PERFORMER)
            .assertTextEquals("Следующий: Мария Сетова")

        composeRule.onNodeWithTag("lineup.entry.statusAction.entry-2.on_stage")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals("event-1", updatedEventId)
        assertEquals("entry-2", updatedEntryId)
        assertEquals(LineupEntryStatus.ON_STAGE, updatedStatus)
    }

    /** Поднимает вкладку в Material-теме для проверки UI-поведения. */
    private fun setLineupContent(
        bindings: LineupTabBindings,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    LineupManagementTab(
                        lineupBindings = bindings,
                    )
                }
            }
        }
    }
}
