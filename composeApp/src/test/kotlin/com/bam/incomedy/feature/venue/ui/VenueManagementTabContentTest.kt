package com.bam.incomedy.feature.venue.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.bam.incomedy.feature.main.ui.ComposeUiTestActivity
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-тесты organizer venue management вкладки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class VenueManagementTabContentTest {

    /** Правило Compose, которое поднимает venue tab в JVM-тесте. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет, что вкладка показывает список площадок и вложенный hall template. */
    @Test
    fun venueTabShowsVenueCardsAndTemplates() {
        setVenueContent()

        composeRule.onNodeWithTag(VenueScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(VenueScreenTags.COUNT).assertTextContains("Площадок: 1")
        composeRule.onNodeWithTag("${VenueScreenTags.VENUE_CARD_PREFIX}venue-1")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("${VenueScreenTags.TEMPLATE_EDIT_PREFIX}template-1")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Проверяет, что форма создания площадки отправляет значения в callback. */
    @Test
    fun venueCreateFormSendsSelectedWorkspaceAndFields() {
        var capturedForm: VenueCreateForm? = null

        setVenueContent(
            venueBindings = VenueTabBindings(
                state = AndroidUiStateFactory.venueState(venues = emptyList()),
                onCreateVenue = { form -> capturedForm = form },
            ),
        )

        composeRule.onNodeWithTag(VenueScreenTags.VENUE_NAME_INPUT).performTextInput("Late Night Hall")
        composeRule.onNodeWithTag(VenueScreenTags.VENUE_CITY_INPUT).performTextInput("Moscow")
        composeRule.onNodeWithTag(VenueScreenTags.VENUE_ADDRESS_INPUT).performTextInput("Tverskaya 9")
        composeRule.onNodeWithTag(VenueScreenTags.VENUE_CAPACITY_INPUT).performTextReplacement("180")
        composeRule.onNodeWithTag(VenueScreenTags.VENUE_CONTACTS_INPUT).performTextReplacement("Telegram|@latehall")
        composeRule.onNodeWithTag(VenueScreenTags.CREATE_BUTTON)
            .performScrollTo()
            .performClick()

        assertEquals(
            VenueCreateForm(
                workspaceId = "ws-1",
                name = "Late Night Hall",
                city = "Moscow",
                address = "Tverskaya 9",
                timezone = "Europe/Moscow",
                capacityText = "180",
                description = "",
                contactsText = "Telegram|@latehall",
            ),
            capturedForm,
        )
    }

    /** Проверяет edit и clone действия над существующим hall template. */
    @Test
    fun venueTemplateEditAndCloneInvokeCallbacks() {
        var savedForm: HallTemplateEditorForm? = null
        var clonedTemplateId: String? = null
        var clonedName: String? = null

        setVenueContent(
            venueBindings = VenueTabBindings(
                state = AndroidUiStateFactory.venueState(),
                onSaveHallTemplate = { form -> savedForm = form },
                onCloneHallTemplate = { templateId, name ->
                    clonedTemplateId = templateId
                    clonedName = name
                },
            ),
        )

        composeRule.onNodeWithTag("${VenueScreenTags.TEMPLATE_EDIT_PREFIX}template-1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(VenueScreenTags.TEMPLATE_NAME_INPUT)
            .assertTextContains("Late Layout")
        composeRule.onNodeWithTag(VenueScreenTags.TEMPLATE_NAME_INPUT)
            .performTextReplacement("Late Layout Updated")
        composeRule.onNodeWithTag(VenueScreenTags.TEMPLATE_SAVE_BUTTON)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("${VenueScreenTags.TEMPLATE_CLONE_PREFIX}template-1")
            .performScrollTo()
            .performClick()

        assertEquals(
            HallTemplateEditorForm(
                venueId = "venue-1",
                templateId = "template-1",
                name = "Late Layout Updated",
                statusKey = "published",
                stageLabel = "",
                priceZonesText = "vip|VIP|3000",
                zonesText = "",
                rowsText = "row-a|A|3|vip",
                tablesText = "",
                serviceAreasText = "",
                blockedSeatRefsText = "row-a-3",
            ),
            savedForm,
        )
        assertEquals("template-1", clonedTemplateId)
        assertEquals("Late Layout копия", clonedName)
    }

    /** Поднимает venue tab в Material-теме для Compose UI-тестов. */
    private fun setVenueContent(
        workspaces: List<com.bam.incomedy.domain.session.OrganizerWorkspace> = listOf(AndroidUiStateFactory.workspace()),
        venueBindings: VenueTabBindings = VenueTabBindings(
            state = AndroidUiStateFactory.venueState(),
        ),
    ) {
        composeRule.setContent {
            MaterialTheme {
                Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState()),
                ) {
                    VenueManagementTab(
                        workspaces = workspaces,
                        venueBindings = venueBindings,
                    )
                }
            }
        }
    }
}
