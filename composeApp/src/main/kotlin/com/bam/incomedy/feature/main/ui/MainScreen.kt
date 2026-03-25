package com.bam.incomedy.feature.main.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.feature.event.ui.EventManagementTab
import com.bam.incomedy.feature.event.ui.EventScreenTags
import com.bam.incomedy.feature.event.ui.EventTabBindings
import com.bam.incomedy.feature.event.viewmodel.EventAndroidViewModel
import com.bam.incomedy.feature.lineup.ui.LineupManagementTab
import com.bam.incomedy.feature.lineup.ui.LineupScreenTags
import com.bam.incomedy.feature.lineup.ui.LineupTabBindings
import com.bam.incomedy.feature.lineup.viewmodel.LineupAndroidViewModel
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import com.bam.incomedy.feature.ticketing.ui.TicketWalletTab
import com.bam.incomedy.feature.ticketing.ui.TicketingScreenTags
import com.bam.incomedy.feature.ticketing.ui.TicketingTabBindings
import com.bam.incomedy.feature.ticketing.viewmodel.TicketingAndroidViewModel
import com.bam.incomedy.feature.venue.ui.VenueManagementTab
import com.bam.incomedy.feature.venue.ui.VenueScreenTags
import com.bam.incomedy.feature.venue.ui.VenueTabBindings
import com.bam.incomedy.feature.venue.viewmodel.VenueAndroidViewModel
import com.bam.incomedy.shared.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Экран авторизованной части приложения с нижним меню и вкладкой аккаунта.
 *
 * @property sessionViewModel Общая модель сессии, которая отдает профиль, роли и рабочие пространства.
 * @property eventViewModel Android-адаптер organizer event feature.
 * @property lineupViewModel Android-адаптер comedian applications и organizer lineup feature.
 * @property ticketingViewModel Android-адаптер audience/staff ticketing feature.
 * @property venueViewModel Android-адаптер organizer venue management feature.
 * @property modifier Внешний модификатор экрана.
 */
@Composable
fun MainScreen(
    sessionViewModel: SessionAndroidViewModel,
    eventViewModel: EventAndroidViewModel,
    lineupViewModel: LineupAndroidViewModel,
    ticketingViewModel: TicketingAndroidViewModel,
    venueViewModel: VenueAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    val eventState by eventViewModel.state.collectAsStateWithLifecycle()
    val lineupState by lineupViewModel.state.collectAsStateWithLifecycle()
    val ticketingState by ticketingViewModel.state.collectAsStateWithLifecycle()
    val venueState by venueViewModel.state.collectAsStateWithLifecycle()

    MainScreenContent(
        state = state,
        eventBindings = EventTabBindings(
            state = eventState,
            onRefreshContext = eventViewModel::refreshContext,
            onCreateEvent = { form ->
                eventViewModel.createEvent(
                    workspaceId = form.workspaceId,
                    venueId = form.venueId,
                    hallTemplateId = form.hallTemplateId,
                    title = form.title,
                    description = form.description,
                    startsAtIso = form.startsAtIso,
                    doorsOpenAtIso = form.doorsOpenAtIso,
                    endsAtIso = form.endsAtIso,
                    currency = form.currency,
                    visibilityKey = form.visibilityKey,
                )
            },
            onUpdateEvent = { form ->
                eventViewModel.updateEvent(
                    eventId = form.eventId,
                    title = form.title,
                    description = form.description,
                    startsAtIso = form.startsAtIso,
                    doorsOpenAtIso = form.doorsOpenAtIso,
                    endsAtIso = form.endsAtIso,
                    currency = form.currency,
                    visibilityKey = form.visibilityKey,
                    priceZonesText = form.priceZonesText,
                    pricingAssignmentsText = form.pricingAssignmentsText,
                    availabilityOverridesText = form.availabilityOverridesText,
                )
            },
            onPublishEvent = eventViewModel::publishEvent,
            onOpenEventSales = eventViewModel::openEventSales,
            onPauseEventSales = eventViewModel::pauseEventSales,
            onCancelEvent = eventViewModel::cancelEvent,
            onClearError = eventViewModel::clearError,
        ),
        lineupBindings = LineupTabBindings(
            state = lineupState,
            organizerEvents = eventState.events,
            onLoadOrganizerContext = lineupViewModel::loadOrganizerContext,
            onSubmitApplication = lineupViewModel::submitApplication,
            onUpdateApplicationStatus = lineupViewModel::updateApplicationStatus,
            onReorderLineup = lineupViewModel::reorderLineup,
            onUpdateLineupEntryStatus = lineupViewModel::updateLineupEntryStatus,
            onSetLiveUpdatesActive = lineupViewModel::setLiveUpdatesActive,
            onClearError = lineupViewModel::clearError,
        ),
        ticketingBindings = TicketingTabBindings(
            state = ticketingState,
            onRefreshTickets = ticketingViewModel::refreshTickets,
            onScanTicket = ticketingViewModel::scanTicket,
            onClearError = ticketingViewModel::clearError,
            onClearCheckInResult = ticketingViewModel::clearCheckInResult,
        ),
        venueBindings = VenueTabBindings(
            state = venueState,
            onRefreshVenues = venueViewModel::refreshVenues,
            onCreateVenue = { form ->
                venueViewModel.createVenue(
                    workspaceId = form.workspaceId,
                    name = form.name,
                    city = form.city,
                    address = form.address,
                    timezone = form.timezone,
                    capacityText = form.capacityText,
                    description = form.description,
                    contactsText = form.contactsText,
                )
            },
            onSaveHallTemplate = { form ->
                venueViewModel.saveHallTemplate(
                    venueId = form.venueId,
                    templateId = form.templateId,
                    name = form.name,
                    statusKey = form.statusKey,
                    stageLabel = form.stageLabel,
                    priceZonesText = form.priceZonesText,
                    zonesText = form.zonesText,
                    rowsText = form.rowsText,
                    tablesText = form.tablesText,
                    serviceAreasText = form.serviceAreasText,
                    blockedSeatRefsText = form.blockedSeatRefsText,
                )
            },
            onCloneHallTemplate = venueViewModel::cloneHallTemplate,
            onClearError = venueViewModel::clearError,
        ),
        onSetActiveRole = sessionViewModel::setActiveRole,
        onCreateWorkspace = sessionViewModel::createWorkspace,
        onCreateWorkspaceInvitation = sessionViewModel::createWorkspaceInvitation,
        onAcceptWorkspaceInvitation = sessionViewModel::acceptWorkspaceInvitation,
        onDeclineWorkspaceInvitation = sessionViewModel::declineWorkspaceInvitation,
        onUpdateWorkspaceMembershipRole = sessionViewModel::updateWorkspaceMembershipRole,
        onClearError = sessionViewModel::clearError,
        onSignOut = sessionViewModel::signOut,
        modifier = modifier,
    )
}

/**
 * Тестируемое содержимое главного экрана без привязки к Android `ViewModel`.
 *
 * @property state Данные текущей сессии для отрисовки вкладок.
 * @property eventBindings Данные и команды organizer event вкладки.
 * @property lineupBindings Данные и команды comedian applications / lineup вкладки.
 * @property ticketingBindings Данные и команды audience/staff ticketing вкладки.
 * @property onSetActiveRole Обработчик переключения активной роли.
 * @property onCreateWorkspace Обработчик создания рабочего пространства.
 * @property onCreateWorkspaceInvitation Обработчик создания invitation внутри workspace.
 * @property onAcceptWorkspaceInvitation Обработчик принятия pending invitation.
 * @property onDeclineWorkspaceInvitation Обработчик отклонения pending invitation.
 * @property onUpdateWorkspaceMembershipRole Обработчик смены permission role membership.
 * @property venueBindings Данные и команды organizer venue management вкладки.
 * @property onClearError Обработчик скрытия ошибки.
 * @property onSignOut Обработчик выхода из профиля.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun MainScreenContent(
    state: SessionState,
    eventBindings: EventTabBindings = EventTabBindings(),
    lineupBindings: LineupTabBindings = LineupTabBindings(),
    ticketingBindings: TicketingTabBindings = TicketingTabBindings(),
    venueBindings: VenueTabBindings = VenueTabBindings(),
    onSetActiveRole: (String) -> Unit,
    onCreateWorkspace: (String, String?) -> Unit,
    onCreateWorkspaceInvitation: (String, String, String) -> Unit,
    onAcceptWorkspaceInvitation: (String) -> Unit,
    onDeclineWorkspaceInvitation: (String) -> Unit,
    onUpdateWorkspaceMembershipRole: (String, String, String) -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /** Хранит текущую выбранную вкладку нижнего меню. */
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(MainScreenTags.ROOT),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag(MainScreenTags.BOTTOM_BAR),
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.iconGlyph) },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag(tab.testTag),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorBanner(
                errorMessage = state.errorMessage,
                onClearError = onClearError,
            )

            if (state.isLoadingContext || state.isUpdatingRole || state.isCreatingWorkspace || state.isManagingWorkspaceMembers) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(MainScreenTags.LOADING),
                )
            }

            when (selectedTab) {
                MainTab.HOME -> MainHomeTab(
                    state = state,
                    onCreateWorkspace = onCreateWorkspace,
                    onCreateWorkspaceInvitation = onCreateWorkspaceInvitation,
                    onAcceptWorkspaceInvitation = onAcceptWorkspaceInvitation,
                    onDeclineWorkspaceInvitation = onDeclineWorkspaceInvitation,
                    onUpdateWorkspaceMembershipRole = onUpdateWorkspaceMembershipRole,
                )
                MainTab.TICKETS -> TicketWalletTab(
                    sessionState = state,
                    ticketingBindings = ticketingBindings,
                    modifier = Modifier.testTag(TicketingScreenTags.ROOT),
                )
                MainTab.ACCOUNT -> AccountTab(
                    state = state,
                    onSetActiveRole = onSetActiveRole,
                    onSignOut = onSignOut,
                )
                MainTab.EVENTS -> EventManagementTab(
                    workspaces = state.workspaces,
                    eventBindings = eventBindings,
                    modifier = Modifier.testTag(EventScreenTags.ROOT),
                )
                MainTab.LINEUP -> LineupManagementTab(
                    lineupBindings = lineupBindings,
                    modifier = Modifier.testTag(LineupScreenTags.ROOT),
                )
                MainTab.VENUES -> VenueManagementTab(
                    workspaces = state.workspaces,
                    venueBindings = venueBindings,
                    modifier = Modifier.testTag(VenueScreenTags.ROOT),
                )
            }
        }
    }
}

/**
 * Блок с ошибкой верхнего уровня для главного экрана.
 *
 * @property errorMessage Текст ошибки, если он есть.
 * @property onClearError Колбэк очистки ошибки.
 */
@Composable
private fun ErrorBanner(
    errorMessage: String?,
    onClearError: () -> Unit,
) {
    if (errorMessage == null) return

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MainScreenTags.ERROR_BANNER),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onClearError,
                modifier = Modifier.widthIn(min = 120.dp),
            ) {
                Text("Скрыть")
            }
        }
    }
}

/**
 * Вкладка сводки, на которой показываются рабочие пространства и форма их создания.
 *
 * @property state Данные сессии для отрисовки списка рабочих пространств.
 * @property onCreateWorkspace Обработчик создания нового рабочего пространства.
 * @property onCreateWorkspaceInvitation Обработчик создания invitation внутри workspace.
 * @property onAcceptWorkspaceInvitation Обработчик принятия pending invitation.
 * @property onDeclineWorkspaceInvitation Обработчик отклонения pending invitation.
 * @property onUpdateWorkspaceMembershipRole Обработчик смены роли участника workspace.
 */
@Composable
private fun MainHomeTab(
    state: SessionState,
    onCreateWorkspace: (String, String?) -> Unit,
    onCreateWorkspaceInvitation: (String, String, String) -> Unit,
    onAcceptWorkspaceInvitation: (String) -> Unit,
    onDeclineWorkspaceInvitation: (String) -> Unit,
    onUpdateWorkspaceMembershipRole: (String, String, String) -> Unit,
) {
    /** Хранит имя создаваемого рабочего пространства. */
    var workspaceName by remember { mutableStateOf("") }
    /** Хранит необязательный slug для создаваемого рабочего пространства. */
    var workspaceSlug by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MainScreenTags.HOME_CONTENT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Главная",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = state.displayName?.let { "Рады видеть, $it" } ?: "Рады видеть снова",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Рабочих пространств:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = state.workspaces.size.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(MainScreenTags.WORKSPACE_COUNT),
            )
        }
        HorizontalDivider()
        if (state.workspaceInvitations.isNotEmpty()) {
            InvitationInboxSection(
                invitations = state.workspaceInvitations,
                isManagingWorkspaceMembers = state.isManagingWorkspaceMembers,
                onAcceptWorkspaceInvitation = onAcceptWorkspaceInvitation,
                onDeclineWorkspaceInvitation = onDeclineWorkspaceInvitation,
            )
            HorizontalDivider()
        }
        if (state.workspaces.isEmpty()) {
            Text(
                text = "Пока нет рабочих пространств",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MainScreenTags.WORKSPACE_EMPTY),
            )
        } else {
            state.workspaces.forEach { workspace ->
                WorkspaceCard(
                    workspace = workspace,
                    isManagingWorkspaceMembers = state.isManagingWorkspaceMembers,
                    onCreateWorkspaceInvitation = onCreateWorkspaceInvitation,
                    onUpdateWorkspaceMembershipRole = onUpdateWorkspaceMembershipRole,
                )
            }
        }
        HorizontalDivider()
        Text(
            text = "Создать рабочее пространство",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = workspaceName,
            onValueChange = { workspaceName = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MainScreenTags.WORKSPACE_NAME_INPUT),
            label = { Text("Название") },
            singleLine = true,
        )
        OutlinedTextField(
            value = workspaceSlug,
            onValueChange = { workspaceSlug = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MainScreenTags.WORKSPACE_SLUG_INPUT),
            label = { Text("Slug (необязательно)") },
            singleLine = true,
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MainScreenTags.CREATE_WORKSPACE_BUTTON),
            enabled = workspaceName.trim().length in 3..80 && !state.isCreatingWorkspace,
            onClick = {
                onCreateWorkspace(
                    workspaceName,
                    workspaceSlug.ifBlank { null },
                )
            },
        ) {
            Text("Создать рабочее пространство")
        }
    }
}

/**
 * Карточка рабочего пространства для главной вкладки.
 *
 * @property workspace Данные рабочего пространства.
 */
@Composable
private fun WorkspaceCard(
    workspace: OrganizerWorkspace,
    isManagingWorkspaceMembers: Boolean,
    onCreateWorkspaceInvitation: (String, String, String) -> Unit,
    onUpdateWorkspaceMembershipRole: (String, String, String) -> Unit,
) {
    /** Хранит login/username пользователя, которого хотят пригласить в workspace. */
    var inviteeIdentifier by remember(workspace.id) { mutableStateOf("") }
    /** Хранит выбранную permission role для нового приглашения. */
    var selectedInviteRole by remember(workspace.id, workspace.assignablePermissionRoles) {
        mutableStateOf(workspace.assignablePermissionRoles.firstOrNull().orEmpty())
    }

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${MainScreenTags.WORKSPACE_CARD_PREFIX}${workspace.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${workspace.slug} · ${permissionRoleTitle(workspace.permissionRole)} · ${workspaceStatusTitle(workspace.status)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (workspace.memberships.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Команда",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("${MainScreenTags.WORKSPACE_TEAM_PREFIX}${workspace.id}"),
                )
                workspace.memberships.forEach { membership ->
                    WorkspaceMembershipRow(
                        workspaceId = workspace.id,
                        membership = membership,
                        isManagingWorkspaceMembers = isManagingWorkspaceMembers,
                        onUpdateWorkspaceMembershipRole = onUpdateWorkspaceMembershipRole,
                    )
                }
            }
            if (workspace.canManageMembers && workspace.assignablePermissionRoles.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Пригласить участника",
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = inviteeIdentifier,
                    onValueChange = { inviteeIdentifier = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${MainScreenTags.WORKSPACE_INVITEE_INPUT_PREFIX}${workspace.id}"),
                    label = { Text("Логин или username") },
                    singleLine = true,
                )
                PermissionRoleSelector(
                    roles = workspace.assignablePermissionRoles,
                    selectedRole = selectedInviteRole,
                    onSelectRole = { selectedInviteRole = it },
                    tagPrefix = "${MainScreenTags.WORKSPACE_INVITE_ROLE_PREFIX}${workspace.id}.",
                )
                Button(
                    onClick = {
                        onCreateWorkspaceInvitation(
                            workspace.id,
                            inviteeIdentifier,
                            selectedInviteRole,
                        )
                    },
                    enabled = inviteeIdentifier.trim().length in 3..80 &&
                        selectedInviteRole.isNotBlank() &&
                        !isManagingWorkspaceMembers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${MainScreenTags.WORKSPACE_INVITE_BUTTON_PREFIX}${workspace.id}"),
                ) {
                    Text("Отправить приглашение")
                }
            }
        }
    }
}

/**
 * Секция pending invitations текущего пользователя.
 *
 * @property invitations Pending invitations в organizer workspaces.
 * @property isManagingWorkspaceMembers Показывает, что сейчас выполняется membership mutation.
 * @property onAcceptWorkspaceInvitation Обработчик принятия invitation.
 * @property onDeclineWorkspaceInvitation Обработчик отклонения invitation.
 */
@Composable
private fun InvitationInboxSection(
    invitations: List<OrganizerWorkspaceInvitation>,
    isManagingWorkspaceMembers: Boolean,
    onAcceptWorkspaceInvitation: (String) -> Unit,
    onDeclineWorkspaceInvitation: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(MainScreenTags.WORKSPACE_INVITATION_INBOX),
    ) {
        Text(
            text = "Ожидают решения",
            style = MaterialTheme.typography.titleMedium,
        )
        invitations.forEach { invitation ->
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${MainScreenTags.WORKSPACE_INVITATION_CARD_PREFIX}${invitation.membershipId}"),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = invitation.workspaceName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "${permissionRoleTitle(invitation.permissionRole)} · ${workspaceStatusTitle(invitation.workspaceStatus)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    invitation.invitedByDisplayName?.let { inviterName ->
                        Text(
                            text = "Пригласил: $inviterName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onAcceptWorkspaceInvitation(invitation.membershipId) },
                            enabled = !isManagingWorkspaceMembers,
                            modifier = Modifier.testTag("${MainScreenTags.WORKSPACE_INVITATION_ACCEPT_PREFIX}${invitation.membershipId}"),
                        ) {
                            Text("Принять")
                        }
                        OutlinedButton(
                            onClick = { onDeclineWorkspaceInvitation(invitation.membershipId) },
                            enabled = !isManagingWorkspaceMembers,
                            modifier = Modifier.testTag("${MainScreenTags.WORKSPACE_INVITATION_DECLINE_PREFIX}${invitation.membershipId}"),
                        ) {
                            Text("Отклонить")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка одного участника или pending invitation внутри workspace.
 *
 * @property workspaceId Идентификатор workspace, которому принадлежит запись.
 * @property membership Membership или pending invitation для отображения.
 * @property isManagingWorkspaceMembers Показывает, что membership mutation уже выполняется.
 * @property onUpdateWorkspaceMembershipRole Обработчик смены permission role.
 */
@Composable
private fun WorkspaceMembershipRow(
    workspaceId: String,
    membership: OrganizerWorkspaceMembership,
    isManagingWorkspaceMembers: Boolean,
    onUpdateWorkspaceMembershipRole: (String, String, String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = membership.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(permissionRoleTitle(membership.permissionRole))
                    append(" · ")
                    append(workspaceMembershipStatusTitle(membership.status))
                    membership.username?.takeIf { it.isNotBlank() }?.let {
                        append(" · @")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            membership.invitedByDisplayName?.takeIf { membership.status == "invited" }?.let { inviterName ->
                Text(
                    text = "Пригласил: $inviterName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (membership.canEditRole && membership.assignablePermissionRoles.isNotEmpty()) {
                PermissionRoleSelector(
                    roles = membership.assignablePermissionRoles,
                    selectedRole = membership.permissionRole,
                    onSelectRole = { role ->
                        onUpdateWorkspaceMembershipRole(
                            workspaceId,
                            membership.membershipId,
                            role,
                        )
                    },
                    enabled = !isManagingWorkspaceMembers,
                    tagPrefix = "${MainScreenTags.WORKSPACE_MEMBERSHIP_ROLE_PREFIX}${membership.membershipId}.",
                )
            }
        }
    }
}

/**
 * Группа кнопок выбора permission role.
 *
 * @property roles Роли, которые можно выбрать.
 * @property selectedRole Текущая выбранная роль.
 * @property onSelectRole Обработчик выбора роли.
 * @property enabled Показывает, что выбор разрешен.
 * @property tagPrefix Префикс тестовых тегов для кнопок ролей.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionRoleSelector(
    roles: List<String>,
    selectedRole: String,
    onSelectRole: (String) -> Unit,
    enabled: Boolean = true,
    tagPrefix: String,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        roles.forEach { role ->
            val isSelected = selectedRole == role
            OutlinedButton(
                onClick = { onSelectRole(role) },
                enabled = enabled,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.testTag("$tagPrefix$role"),
            ) {
                Text(permissionRoleTitle(role))
            }
        }
    }
}

/**
 * Вкладка аккаунта, где показываются данные профиля, фото, роли и действие выхода.
 *
 * @property state Данные текущего профиля.
 * @property onSetActiveRole Обработчик смены роли.
 * @property onSignOut Обработчик выхода.
 */
@Composable
private fun AccountTab(
    state: SessionState,
    onSetActiveRole: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MainScreenTags.ACCOUNT_CONTENT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Аккаунт",
            style = MaterialTheme.typography.headlineSmall,
        )
        AccountHeader(state = state)
        HorizontalDivider()
        ProfileField(
            title = "Имя профиля",
            value = state.displayName ?: "Не указано",
            valueTag = MainScreenTags.PROFILE_NAME,
        )
        ProfileField(
            title = "Username",
            value = state.username ?: "Не указан",
            valueTag = MainScreenTags.PROFILE_USERNAME,
        )
        ProfileField(
            title = "ID пользователя",
            value = state.userId ?: "Недоступен",
            valueTag = MainScreenTags.PROFILE_USER_ID,
        )
        ProfileField(
            title = "Привязанные входы",
            value = state.linkedProviders.joinToString(", ", transform = ::providerTitle).ifBlank { "Не указаны" },
            valueTag = MainScreenTags.PROFILE_LINKED_PROVIDERS,
        )
        HorizontalDivider()
        Text(
            text = "Смена ролей",
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.roles.isEmpty()) {
            Text(
                text = "Роли пока не назначены",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MainScreenTags.NO_ROLES),
            )
        } else {
            state.roles.forEach { role ->
                val isActive = state.activeRole == role
                OutlinedButton(
                    onClick = { onSetActiveRole(role) },
                    enabled = !state.isUpdatingRole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${MainScreenTags.ROLE_BUTTON_PREFIX}$role"),
                    border = BorderStroke(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Text(
                        text = if (isActive) {
                            "${roleTitle(role)} · активна"
                        } else {
                            roleTitle(role)
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MainScreenTags.SIGN_OUT_BUTTON),
            onClick = onSignOut,
        ) {
            Text("Выйти")
        }
    }
}

/**
 * Верхний блок профиля с фото или заглушкой, именем и активной ролью.
 *
 * @property state Состояние профиля пользователя.
 */
@Composable
private fun AccountHeader(
    state: SessionState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            photoUrl = state.photoUrl,
            displayName = state.displayName,
            modifier = Modifier.testTag(MainScreenTags.ACCOUNT_AVATAR),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.displayName ?: "Профиль",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.activeRole?.let(::roleTitle) ?: "Роль не выбрана",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(MainScreenTags.ACTIVE_ROLE),
            )
        }
    }
}

/**
 * Строка с названием и значением поля профиля.
 *
 * @property title Название поля профиля.
 * @property value Значение поля профиля.
 * @property modifier Модификатор строки.
 * @property valueTag Необязательный тег для значения поля профиля.
 */
@Composable
private fun ProfileField(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTag: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = valueTag
                ?.let(Modifier::testTag)
                ?: Modifier,
        )
    }
}

/**
 * Аватар пользователя, который пытается показать удаленное фото, а при ошибке показывает инициалы.
 *
 * @property photoUrl Ссылка на фото профиля.
 * @property displayName Имя пользователя для генерации инициалов.
 * @property modifier Модификатор контейнера.
 */
@Composable
private fun ProfileAvatar(
    photoUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier,
) {
    /** Хранит загруженное изображение профиля, если сеть вернула корректный ответ. */
    val imageState = rememberProfileImage(photoUrl = photoUrl)
    /** Содержит букву для текстовой заглушки аватара. */
    val fallbackLetter = displayName
        ?.trim()
        ?.firstOrNull()
        ?.uppercase()
        ?: "?"

    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = imageState.value
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Фото профиля",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallbackLetter,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag(MainScreenTags.ACCOUNT_AVATAR_FALLBACK),
            )
        }
    }
}

/**
 * Загружает изображение профиля по URL для Android-экрана аккаунта.
 *
 * @property photoUrl Ссылка на фото профиля.
 */
@Composable
private fun rememberProfileImage(
    photoUrl: String?,
): State<ImageBitmap?> {
    return produceState<ImageBitmap?>(initialValue = null, photoUrl) {
        if (photoUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                URL(photoUrl).openStream().use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
}

/**
 * Доступные вкладки нижнего меню главного экрана.
 *
 * @property title Подпись вкладки.
 * @property iconGlyph Текстовая иконка для нижней панели.
 * @property testTag Тег для UI-тестов.
 */
private enum class MainTab(
    val title: String,
    val iconGlyph: String,
    val testTag: String,
) {
    HOME(
        title = "Главная",
        iconGlyph = "⌂",
        testTag = MainScreenTags.TAB_HOME,
    ),
    TICKETS(
        title = "Билеты",
        iconGlyph = "◩",
        testTag = MainScreenTags.TAB_TICKETS,
    ),
    VENUES(
        title = "Площадки",
        iconGlyph = "▦",
        testTag = MainScreenTags.TAB_VENUES,
    ),
    EVENTS(
        title = "События",
        iconGlyph = "◫",
        testTag = MainScreenTags.TAB_EVENTS,
    ),
    LINEUP(
        title = "Лайнап",
        iconGlyph = "≣",
        testTag = MainScreenTags.TAB_LINEUP,
    ),
    ACCOUNT(
        title = "Аккаунт",
        iconGlyph = "☺",
        testTag = MainScreenTags.TAB_ACCOUNT,
    ),
}

/**
 * Набор тегов, по которым UI-тесты находят ключевые элементы главного экрана.
 */
object MainScreenTags {
    /** Тег корневого контейнера главного экрана. */
    const val ROOT = "main.root"

    /** Тег нижней панели навигации. */
    const val BOTTOM_BAR = "main.bottomBar"

    /** Тег вкладки главной сводки. */
    const val TAB_HOME = "main.tab.home"

    /** Тег вкладки аккаунта. */
    const val TAB_ACCOUNT = "main.tab.account"

    /** Тег вкладки билетов. */
    const val TAB_TICKETS = "main.tab.tickets"

    /** Тег вкладки площадок. */
    const val TAB_VENUES = "main.tab.venues"

    /** Тег вкладки событий. */
    const val TAB_EVENTS = "main.tab.events"

    /** Тег вкладки лайнапа и заявок. */
    const val TAB_LINEUP = "main.tab.lineup"

    /** Тег контейнера вкладки главной сводки. */
    const val HOME_CONTENT = "main.content.home"

    /** Тег контейнера вкладки аккаунта. */
    const val ACCOUNT_CONTENT = "main.content.account"

    /** Тег индикатора загрузки контекста. */
    const val LOADING = "main.loading"

    /** Тег баннера ошибки. */
    const val ERROR_BANNER = "main.error"

    /** Тег поля имени рабочего пространства. */
    const val WORKSPACE_NAME_INPUT = "main.workspace.name"

    /** Тег поля slug рабочего пространства. */
    const val WORKSPACE_SLUG_INPUT = "main.workspace.slug"

    /** Тег кнопки создания рабочего пространства. */
    const val CREATE_WORKSPACE_BUTTON = "main.workspace.create"

    /** Тег блока аватара аккаунта. */
    const val ACCOUNT_AVATAR = "main.account.avatar"

    /** Тег поля имени в профиле. */
    const val PROFILE_NAME = "main.account.name"

    /** Тег поля username в профиле. */
    const val PROFILE_USERNAME = "main.account.username"

    /** Тег поля внутреннего user id в профиле. */
    const val PROFILE_USER_ID = "main.account.userId"

    /** Тег поля привязанных способов входа. */
    const val PROFILE_LINKED_PROVIDERS = "main.account.linkedProviders"

    /** Тег поля количества рабочих пространств. */
    const val WORKSPACE_COUNT = "main.workspace.count"

    /** Тег пустого состояния списка рабочих пространств. */
    const val WORKSPACE_EMPTY = "main.workspace.empty"

    /** Тег секции inbox pending invitations. */
    const val WORKSPACE_INVITATION_INBOX = "main.workspace.invitations"

    /** Префикс карточек pending invitations. */
    const val WORKSPACE_INVITATION_CARD_PREFIX = "main.workspace.invitation.card."

    /** Префикс кнопок принятия pending invitations. */
    const val WORKSPACE_INVITATION_ACCEPT_PREFIX = "main.workspace.invitation.accept."

    /** Префикс кнопок отклонения pending invitations. */
    const val WORKSPACE_INVITATION_DECLINE_PREFIX = "main.workspace.invitation.decline."

    /** Префикс карточек рабочих пространств. */
    const val WORKSPACE_CARD_PREFIX = "main.workspace.card."

    /** Префикс заголовка команды внутри workspace. */
    const val WORKSPACE_TEAM_PREFIX = "main.workspace.team."

    /** Префикс полей ввода invitee identifier внутри workspace. */
    const val WORKSPACE_INVITEE_INPUT_PREFIX = "main.workspace.invitee."

    /** Префикс кнопок отправки invitation внутри workspace. */
    const val WORKSPACE_INVITE_BUTTON_PREFIX = "main.workspace.invite."

    /** Префикс кнопок выбора role в invite form. */
    const val WORKSPACE_INVITE_ROLE_PREFIX = "main.workspace.invite.role."

    /** Префикс кнопок смены role у membership записей. */
    const val WORKSPACE_MEMBERSHIP_ROLE_PREFIX = "main.workspace.membership.role."

    /** Тег текста текущей активной роли. */
    const val ACTIVE_ROLE = "main.account.activeRole"

    /** Тег пустого состояния ролей. */
    const val NO_ROLES = "main.account.noRoles"

    /** Тег кнопки выхода. */
    const val SIGN_OUT_BUTTON = "main.account.signOut"

    /** Тег текстовой заглушки аватара без фото. */
    const val ACCOUNT_AVATAR_FALLBACK = "main.account.avatarFallback"

    /** Префикс тега для кнопок смены роли. */
    const val ROLE_BUTTON_PREFIX = "main.account.role."
}

/**
 * Переводит внутренний код роли в человекочитаемый вид.
 *
 * @property role Код роли из состояния сессии.
 */
private fun roleTitle(role: String): String {
    return when (role) {
        "audience" -> "Зритель"
        "comedian" -> "Комик"
        "organizer" -> "Организатор"
        else -> role
    }
}

/**
 * Переводит код способа входа в подпись для UI.
 *
 * @property provider Код привязанного способа входа.
 */
private fun providerTitle(provider: String): String {
    return when (provider) {
        "password" -> "Логин и пароль"
        "phone" -> "Телефон"
        "telegram" -> "Telegram"
        "vk" -> "VK"
        "google" -> "Google"
        "apple" -> "Apple"
        else -> provider
    }
}

/**
 * Переводит код роли доступа в рабочем пространстве в подпись для UI.
 *
 * @property role Код роли доступа в рабочем пространстве.
 */
private fun permissionRoleTitle(role: String): String {
    return when (role) {
        "owner" -> "Владелец"
        "manager" -> "Менеджер"
        "checker" -> "Чекер"
        "host" -> "Ведущий"
        else -> role
    }
}

/**
 * Переводит код статуса рабочего пространства в подпись для UI.
 *
 * @property status Внутренний код статуса рабочего пространства.
 */
private fun workspaceStatusTitle(status: String): String {
    return when (status) {
        "active" -> "Активно"
        else -> status
    }
}

/**
 * Переводит код состояния membership в подпись для UI.
 *
 * @property status Внутренний код состояния membership.
 */
private fun workspaceMembershipStatusTitle(status: String): String {
    return when (status) {
        "active" -> "Активен"
        "invited" -> "Ожидает подтверждения"
        else -> status
    }
}
