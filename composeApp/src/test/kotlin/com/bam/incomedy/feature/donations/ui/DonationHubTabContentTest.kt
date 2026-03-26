package com.bam.incomedy.feature.donations.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
class DonationHubTabContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    @Test
    fun comedianSurfaceShowsPayoutProfileAndSendsSaveAction() {
        var savedLegalType: String? = null
        var savedBeneficiaryRef: String? = null

        composeRule.setContent {
            MaterialTheme {
                DonationHubTab(
                    sessionState = AndroidUiStateFactory.sessionState(
                        roles = listOf("audience", "comedian"),
                    ),
                    donationsBindings = DonationsTabBindings(
                        state = AndroidUiStateFactory.donationsState(),
                        onSavePayoutProfile = { legalType, beneficiaryRef ->
                            savedLegalType = legalType.wireName
                            savedBeneficiaryRef = beneficiaryRef
                        },
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(DonationScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(DonationScreenTags.PAYOUT_STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(DonationScreenTags.SENT_COUNT).assertTextEquals("Отправлено: 1")
        composeRule.onNodeWithTag(DonationScreenTags.RECEIVED_COUNT).assertTextEquals("Получено: 1")
        composeRule.onNodeWithTag("${DonationScreenTags.PAYOUT_LEGAL_TYPE_PREFIX}company").performClick()
        composeRule.onNodeWithTag(DonationScreenTags.PAYOUT_BENEFICIARY_INPUT).performTextReplacement("corp-ref-42")
        composeRule.onNodeWithTag(DonationScreenTags.PAYOUT_SAVE_BUTTON).performClick()

        assertEquals("company", savedLegalType)
        assertEquals("corp-ref-42", savedBeneficiaryRef)
    }

    @Test
    fun nonComedianSurfaceShowsLockedState() {
        composeRule.setContent {
            MaterialTheme {
                DonationHubTab(
                    sessionState = AndroidUiStateFactory.sessionState(
                        roles = listOf("audience"),
                    ),
                    donationsBindings = DonationsTabBindings(
                        state = AndroidUiStateFactory.donationsState(
                            payoutProfile = null,
                            receivedDonations = emptyList(),
                            hasComedianRole = false,
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(DonationScreenTags.PAYOUT_LOCKED).assertIsDisplayed()
        composeRule.onNodeWithTag(DonationScreenTags.SENT_COUNT).assertTextEquals("Отправлено: 1")
    }

    @Test
    fun refreshesWhenAccessTokenBecomesAvailable() {
        val sessionState = mutableStateOf(
            AndroidUiStateFactory.sessionState(accessToken = null),
        )
        var refreshCalls = 0

        composeRule.setContent {
            MaterialTheme {
                DonationHubTab(
                    sessionState = sessionState.value,
                    donationsBindings = DonationsTabBindings(
                        state = AndroidUiStateFactory.donationsState(),
                        onRefresh = { refreshCalls += 1 },
                    ),
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, refreshCalls)

        composeRule.runOnUiThread {
            sessionState.value = sessionState.value.copy(accessToken = "access-token")
        }
        composeRule.waitForIdle()

        assertEquals(1, refreshCalls)
    }
}
