package com.bam.incomedy.feature.main.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.feature.donations.ui.DonationScreenTags
import com.bam.incomedy.feature.donations.ui.DonationsTabBindings
import com.bam.incomedy.feature.ticketing.ui.TicketingScreenTags
import com.bam.incomedy.feature.ticketing.ui.TicketingTabBindings
import com.bam.incomedy.feature.lineup.ui.LineupScreenTags
import com.bam.incomedy.feature.lineup.ui.LineupTabBindings
import com.bam.incomedy.feature.event.ui.EventTabBindings
import com.bam.incomedy.feature.notifications.ui.AnnouncementScreenTags
import com.bam.incomedy.feature.notifications.ui.NotificationsTabBindings
import com.bam.incomedy.testsupport.AndroidUiStateFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * UI-тесты содержимого главного экрана после авторизации.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MainScreenContentTest {

    /** Правило Compose, которое поднимает экран внутри JVM-теста. */
    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeUiTestActivity>()

    /** Проверяет, что домашняя вкладка показывает сводку и рабочие пространства. */
    @Test
    fun homeTabShowsWorkspaceSummary() {
        setMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTags.BOTTOM_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.HOME_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_COUNT).assertTextEquals("1")
        composeRule.onNodeWithText("Moscow Cellar").assertIsDisplayed()
        composeRule.onAllNodesWithTag(MainScreenTags.WORKSPACE_INVITATION_INBOX).assertCountEquals(0)
    }

    /** Проверяет пустое состояние рабочих пространств без привязки к длинной строке счетчика. */
    @Test
    fun homeTabShowsEmptyWorkspaceState() {
        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(
                workspaces = emptyList(),
                workspaceInvitations = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_COUNT).assertTextEquals("0")
        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_EMPTY).assertIsDisplayed()
    }

    /** Показывает глобальный индикатор загрузки, когда контекст сессии еще обновляется. */
    @Test
    fun mainScreenShowsLoadingIndicatorWhileContextLoads() {
        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(isManagingWorkspaceMembers = true),
        )

        composeRule.onNodeWithTag(MainScreenTags.LOADING).assertIsDisplayed()
    }

    /** Проверяет отображение inbox pending invitations и отправку accept/decline действий. */
    @Test
    fun homeTabShowsWorkspaceInvitationsAndInvokesActions() {
        var acceptedInvitationId: String? = null
        var declinedInvitationId: String? = null

        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(
                workspaceInvitations = listOf(AndroidUiStateFactory.workspaceInvitation()),
            ),
            onAcceptWorkspaceInvitation = { acceptedInvitationId = it },
            onDeclineWorkspaceInvitation = { declinedInvitationId = it },
        )

        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_INVITATION_INBOX).assertIsDisplayed()
        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_INVITATION_ACCEPT_PREFIX}wm-invite-1")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_INVITATION_DECLINE_PREFIX}wm-invite-1")
            .performScrollTo()
            .performClick()

        assertEquals("wm-invite-1", acceptedInvitationId)
        assertEquals("wm-invite-1", declinedInvitationId)
    }

    /** Проверяет invitation form внутри workspace и прокидывание выбранной роли. */
    @Test
    fun workspaceCardSendsInvitationIdentifierAndRole() {
        var invitedWorkspaceId: String? = null
        var invitedIdentifier: String? = null
        var invitedRole: String? = null

        setMainScreenContent(
            onCreateWorkspaceInvitation = { workspaceId, identifier, role ->
                invitedWorkspaceId = workspaceId
                invitedIdentifier = identifier
                invitedRole = role
            },
        )

        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_INVITEE_INPUT_PREFIX}ws-1")
            .performTextInput("checker_user")
        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_INVITE_ROLE_PREFIX}ws-1.checker")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_INVITE_BUTTON_PREFIX}ws-1")
            .performScrollTo()
            .performClick()

        assertEquals("ws-1", invitedWorkspaceId)
        assertEquals("checker_user", invitedIdentifier)
        assertEquals("checker", invitedRole)
    }

    /** Проверяет смену роли участника workspace из roster карточки. */
    @Test
    fun workspaceCardInvokesMembershipRoleUpdate() {
        var updatedWorkspaceId: String? = null
        var updatedMembershipId: String? = null
        var updatedRole: String? = null

        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(
                workspaces = listOf(
                    AndroidUiStateFactory.workspace(
                        memberships = listOf(
                            AndroidUiStateFactory.workspaceMembership(
                                membershipId = "wm-2",
                                userId = "user-2",
                                displayName = "Checker User",
                                permissionRole = "checker",
                                status = "active",
                                isCurrentUser = false,
                                canEditRole = true,
                                assignablePermissionRoles = listOf("checker", "host"),
                            ),
                        ),
                    ),
                ),
            ),
            onUpdateWorkspaceMembershipRole = { workspaceId, membershipId, role ->
                updatedWorkspaceId = workspaceId
                updatedMembershipId = membershipId
                updatedRole = role
            },
        )

        composeRule.onNodeWithTag("${MainScreenTags.WORKSPACE_MEMBERSHIP_ROLE_PREFIX}wm-2.host")
            .performScrollTo()
            .performClick()

        assertEquals("ws-1", updatedWorkspaceId)
        assertEquals("wm-2", updatedMembershipId)
        assertEquals("host", updatedRole)
    }

    /** Проверяет, что main shell умеет переключаться на organizer venue вкладку. */
    @Test
    fun venueTabIsReachableFromBottomBar() {
        setMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTags.TAB_VENUES).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(com.bam.incomedy.feature.venue.ui.VenueScreenTags.ROOT).assertIsDisplayed()
    }

    /** Проверяет, что main shell умеет переключаться на organizer event вкладку. */
    @Test
    fun eventTabIsReachableFromBottomBar() {
        setMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTags.TAB_EVENTS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(com.bam.incomedy.feature.event.ui.EventScreenTags.ROOT).assertIsDisplayed()
    }

    /** Проверяет, что main shell умеет переключаться на audience/staff ticketing вкладку. */
    @Test
    fun ticketTabIsReachableFromBottomBar() {
        setMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTags.TAB_TICKETS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TicketingScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TicketingScreenTags.COUNT).assertTextEquals("Билетов: 2")
    }

    /** Проверяет, что main shell умеет переключаться на donations/payout вкладку. */
    @Test
    fun donationTabIsReachableFromBottomBar() {
        setMainScreenContent(
            donationsBindings = DonationsTabBindings(
                state = AndroidUiStateFactory.donationsState(),
            ),
        )

        composeRule.onNodeWithTag(MainScreenTags.TAB_DONATIONS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DonationScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(DonationScreenTags.SENT_COUNT).assertTextEquals("Отправлено: 1")
    }

    /** Проверяет, что main shell умеет переключаться на вкладку announcements/feed. */
    @Test
    fun announcementsTabIsReachableFromBottomBar() {
        setMainScreenContent(
            notificationsBindings = NotificationsTabBindings(
                state = AndroidUiStateFactory.notificationsState(),
                organizerEvents = listOf(
                    AndroidUiStateFactory.event(
                        id = "event-2",
                        status = EventStatus.PUBLISHED,
                        visibility = EventVisibility.PUBLIC,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(MainScreenTags.TAB_ANNOUNCEMENTS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AnnouncementScreenTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AnnouncementScreenTags.COUNT).assertTextEquals("Анонсов: 2")
    }

    /** Проверяет, что main shell умеет переключаться на вкладку лайнапа и заявок. */
    @Test
    fun lineupTabIsReachableFromBottomBar() {
        setMainScreenContent()

        composeRule.onNodeWithTag(MainScreenTags.TAB_LINEUP).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LineupScreenTags.ROOT).assertIsDisplayed()
    }

    /** Проверяет, что ticketing-вкладка отправляет QR payload на проверку. */
    @Test
    fun ticketTabInvokesQrScanCallback() {
        var scannedPayload: String? = null

        setMainScreenContent(
            ticketingBindings = TicketingTabBindings(
                state = AndroidUiStateFactory.ticketingState(),
                onScanTicket = { scannedPayload = it },
            ),
        )

        composeRule.onNodeWithTag(MainScreenTags.TAB_TICKETS).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TicketingScreenTags.SCAN_INPUT)
            .performTextInput("incomedy.ticket.v1:ticket-1")
        composeRule.onNodeWithTag(TicketingScreenTags.SCAN_BUTTON)
            .performScrollTo()
            .performClick()

        assertEquals("incomedy.ticket.v1:ticket-1", scannedPayload)
    }

    /** Проверяет, что вкладка аккаунта показывает профиль и прокидывает действия роли и выхода. */
    @Test
    fun accountTabShowsProfileAndInvokesActions() {
        var requestedRole: String? = null
        var signOutClicks = 0

        setMainScreenContent(
            onSetActiveRole = { requestedRole = it },
            onSignOut = { signOutClicks += 1 },
        )

        composeRule.onNodeWithTag(MainScreenTags.TAB_ACCOUNT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MainScreenTags.ACCOUNT_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.ACCOUNT_AVATAR).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.PROFILE_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag("${MainScreenTags.ROLE_BUTTON_PREFIX}audience")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("${MainScreenTags.ROLE_BUTTON_PREFIX}organizer")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(MainScreenTags.SIGN_OUT_BUTTON)
            .performScrollTo()
            .performClick()

        assertEquals("organizer", requestedRole)
        assertEquals(1, signOutClicks)
    }

    /** Проверяет fallback-поля профиля, если сервер еще не вернул данные пользователя и роли. */
    @Test
    fun accountTabShowsFallbackProfileValues() {
        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(
                userId = null,
                displayName = null,
                username = null,
                roles = emptyList(),
                activeRole = null,
                linkedProviders = emptyList(),
                workspaces = emptyList(),
            ),
        )

        openAccountTab()

        composeRule.onNodeWithTag(MainScreenTags.PROFILE_NAME).assertTextEquals("Не указано")
        composeRule.onNodeWithTag(MainScreenTags.PROFILE_USERNAME).assertTextEquals("Не указан")
        composeRule.onNodeWithTag(MainScreenTags.PROFILE_USER_ID).assertTextEquals("Недоступен")
        composeRule.onNodeWithTag(MainScreenTags.PROFILE_LINKED_PROVIDERS).assertTextEquals("Не указаны")
        composeRule.onNodeWithTag(MainScreenTags.ACTIVE_ROLE).assertTextEquals("Роль не выбрана")
        composeRule.onNodeWithTag(MainScreenTags.NO_ROLES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.ACCOUNT_AVATAR_FALLBACK).assertTextEquals("?")
    }

    /** Проверяет, что кнопки смены роли блокируются на время серверного переключения. */
    @Test
    fun accountTabDisablesRoleButtonsWhileRoleUpdateRuns() {
        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(isUpdatingRole = true),
        )

        openAccountTab()

        composeRule.onNodeWithTag("${MainScreenTags.ROLE_BUTTON_PREFIX}audience")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("${MainScreenTags.ROLE_BUTTON_PREFIX}organizer")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    /** Проверяет форму создания рабочего пространства и передачу необязательного slug. */
    @Test
    fun workspaceFormSendsNameAndBlankSlugAsNull() {
        var createdName: String? = null
        var createdSlug: String? = "sentinel"

        setMainScreenContent(
            onCreateWorkspace = { name, slug ->
                createdName = name
                createdSlug = slug
            },
        )

        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_NAME_INPUT).performTextInput("Fresh Space")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MainScreenTags.CREATE_WORKSPACE_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals("Fresh Space", createdName)
        assertEquals(null, createdSlug)
    }

    /** Проверяет блокировку создания рабочего пространства во время фонового запроса. */
    @Test
    fun workspaceFormDisablesCreateButtonWhileCreateRequestRuns() {
        setMainScreenContent(
            state = AndroidUiStateFactory.sessionState(isCreatingWorkspace = true),
        )

        composeRule.onNodeWithTag(MainScreenTags.WORKSPACE_NAME_INPUT).performTextInput("Fresh Space")
        composeRule.onNodeWithTag(MainScreenTags.CREATE_WORKSPACE_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    /** Проверяет, что баннер ошибки исчезает после очистки состояния. */
    @Test
    fun errorBannerHidesAfterClearCallbackUpdatesState() {
        val state = mutableStateOf(
            AndroidUiStateFactory.sessionState(errorMessage = "Что-то пошло не так"),
        )

        composeRule.setContent {
            MaterialTheme {
                MainScreenContent(
                    state = state.value,
                    onSetActiveRole = {},
                    onCreateWorkspace = { _, _ -> },
                    onCreateWorkspaceInvitation = { _, _, _ -> },
                    onAcceptWorkspaceInvitation = {},
                    onDeclineWorkspaceInvitation = {},
                    onUpdateWorkspaceMembershipRole = { _, _, _ -> },
                    onClearError = { state.value = state.value.copy(errorMessage = null) },
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithTag(MainScreenTags.ERROR_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Скрыть").performClick()
        composeRule.onAllNodesWithTag(MainScreenTags.ERROR_BANNER).assertCountEquals(0)
    }

    /** Поднимает содержимое главного экрана в Material-теме для проверки UI-поведения. */
    private fun setMainScreenContent(
        state: com.bam.incomedy.shared.session.SessionState = AndroidUiStateFactory.sessionState(),
        donationsBindings: DonationsTabBindings = DonationsTabBindings(
            state = AndroidUiStateFactory.donationsState(),
        ),
        notificationsBindings: NotificationsTabBindings = NotificationsTabBindings(
            state = AndroidUiStateFactory.notificationsState(),
            organizerEvents = listOf(
                AndroidUiStateFactory.event(
                    id = "event-2",
                    status = EventStatus.PUBLISHED,
                    visibility = EventVisibility.PUBLIC,
                ),
            ),
        ),
        eventBindings: EventTabBindings = EventTabBindings(
            state = AndroidUiStateFactory.eventState(),
        ),
        lineupBindings: LineupTabBindings = LineupTabBindings(
            state = AndroidUiStateFactory.lineupState(),
            organizerEvents = AndroidUiStateFactory.eventState().events,
        ),
        ticketingBindings: TicketingTabBindings = TicketingTabBindings(
            state = AndroidUiStateFactory.ticketingState(),
        ),
        onSetActiveRole: (String) -> Unit = {},
        onCreateWorkspace: (String, String?) -> Unit = { _, _ -> },
        onCreateWorkspaceInvitation: (String, String, String) -> Unit = { _, _, _ -> },
        onAcceptWorkspaceInvitation: (String) -> Unit = {},
        onDeclineWorkspaceInvitation: (String) -> Unit = {},
        onUpdateWorkspaceMembershipRole: (String, String, String) -> Unit = { _, _, _ -> },
        onClearError: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                MainScreenContent(
                    state = state,
                    donationsBindings = donationsBindings,
                    notificationsBindings = notificationsBindings,
                    eventBindings = eventBindings,
                    lineupBindings = lineupBindings,
                    ticketingBindings = ticketingBindings,
                    onSetActiveRole = onSetActiveRole,
                    onCreateWorkspace = onCreateWorkspace,
                    onCreateWorkspaceInvitation = onCreateWorkspaceInvitation,
                    onAcceptWorkspaceInvitation = onAcceptWorkspaceInvitation,
                    onDeclineWorkspaceInvitation = onDeclineWorkspaceInvitation,
                    onUpdateWorkspaceMembershipRole = onUpdateWorkspaceMembershipRole,
                    onClearError = onClearError,
                    onSignOut = onSignOut,
                )
            }
        }
    }

    /** Переключает тестовый экран на вкладку аккаунта и дожидается завершения композиции. */
    private fun openAccountTab() {
        composeRule.onNodeWithTag(MainScreenTags.TAB_ACCOUNT).performClick()
        composeRule.waitForIdle()
    }
}
