package com.bam.incomedy.testsupport

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.AuthorizedUser
import com.bam.incomedy.domain.event.EventHallSnapshot
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.IssuedTicketStatus
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.session.OrganizerWorkspaceInvitation
import com.bam.incomedy.domain.session.OrganizerWorkspaceMembership
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.domain.venue.VenueContact
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.event.EventState
import com.bam.incomedy.feature.lineup.LineupState
import com.bam.incomedy.feature.ticketing.TicketingState
import com.bam.incomedy.feature.venue.VenueState
import com.bam.incomedy.shared.session.SessionState

/**
 * Фабрика стабильных тестовых состояний для Android UI-тестов.
 */
object AndroidUiStateFactory {

    /**
     * Возвращает типовое состояние авторизованной сессии для post-auth UI-тестов.
     */
    fun sessionState(
        isAuthorized: Boolean = true,
        provider: AuthProviderType = AuthProviderType.PASSWORD,
        accessToken: String? = "access-token",
        refreshToken: String? = "refresh-token",
        userId: String? = "user-1",
        displayName: String? = "Test User",
        username: String? = "test_user",
        photoUrl: String? = null,
        roles: List<String> = listOf("audience", "organizer"),
        activeRole: String? = "audience",
        linkedProviders: List<String> = listOf("password"),
        workspaces: List<OrganizerWorkspace> = listOf(workspace()),
        workspaceInvitations: List<OrganizerWorkspaceInvitation> = emptyList(),
        isLoadingContext: Boolean = false,
        isUpdatingRole: Boolean = false,
        isCreatingWorkspace: Boolean = false,
        isManagingWorkspaceMembers: Boolean = false,
        errorMessage: String? = null,
    ): SessionState {
        return SessionState(
            isAuthorized = isAuthorized,
            provider = provider,
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
            workspaces = workspaces,
            workspaceInvitations = workspaceInvitations,
            isLoadingContext = isLoadingContext,
            isUpdatingRole = isUpdatingRole,
            isCreatingWorkspace = isCreatingWorkspace,
            isManagingWorkspaceMembers = isManagingWorkspaceMembers,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает состояние авторизации для UI-тестов экрана входа.
     */
    fun authState(
        isLoading: Boolean = false,
        selectedProvider: AuthProviderType? = null,
        errorMessage: String? = null,
        session: AuthSession? = null,
    ): AuthState {
        return AuthState(
            isLoading = isLoading,
            selectedProvider = selectedProvider,
            errorMessage = errorMessage,
            session = session,
        )
    }

    /**
     * Возвращает авторизованную сессию для состояния после успешного входа.
     */
    fun authSession(
        provider: AuthProviderType = AuthProviderType.PASSWORD,
        userId: String = "user-1",
        accessToken: String = "access-token",
        refreshToken: String? = "refresh-token",
        user: AuthorizedUser = authorizedUser(),
    ): AuthSession {
        return AuthSession(
            provider = provider,
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user,
        )
    }

    /**
     * Возвращает состояние organizer venue feature для Android UI-тестов.
     */
    fun venueState(
        venues: List<OrganizerVenue> = listOf(venue()),
        isLoading: Boolean = false,
        isSubmitting: Boolean = false,
        errorMessage: String? = null,
    ): VenueState {
        return VenueState(
            venues = venues,
            isLoading = isLoading,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает состояние organizer event feature для Android UI-тестов.
     */
    fun eventState(
        events: List<OrganizerEvent> = listOf(event()),
        venues: List<OrganizerVenue> = listOf(venue()),
        isLoading: Boolean = false,
        isSubmitting: Boolean = false,
        errorMessage: String? = null,
    ): EventState {
        return EventState(
            events = events,
            venues = venues,
            isLoading = isLoading,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает состояние audience/staff ticketing feature для Android UI-тестов.
     */
    fun ticketingState(
        tickets: List<IssuedTicket> = listOf(
            issuedTicket(),
            issuedTicket(
                id = "ticket-2",
                orderId = "order-2",
                eventId = "event-2",
                inventoryUnitId = "inventory-2",
                inventoryRef = "table-b2-seat-3",
                label = "Стол B2 · Место 3",
                status = IssuedTicketStatus.CHECKED_IN,
                qrPayload = "incomedy.ticket.v1:ticket-2",
                issuedAtIso = "2026-04-02T18:00:00+03:00",
                checkedInAtIso = "2026-04-02T18:59:00+03:00",
                checkedInByUserId = "checker-1",
            ),
        ),
        isLoading: Boolean = false,
        isScanning: Boolean = false,
        errorMessage: String? = null,
    ): TicketingState {
        return TicketingState(
            tickets = tickets,
            isLoading = isLoading,
            isScanning = isScanning,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает состояние comedian applications и organizer lineup feature для Android UI-тестов.
     */
    fun lineupState(
        selectedEventId: String = "event-1",
        applications: List<ComedianApplication> = listOf(comedianApplication()),
        lineup: List<LineupEntry> = listOf(lineupEntry()),
        isLoading: Boolean = false,
        isSubmitting: Boolean = false,
        errorMessage: String? = null,
    ): LineupState {
        return LineupState(
            selectedEventId = selectedEventId,
            applications = applications,
            lineup = lineup,
            isLoading = isLoading,
            isSubmitting = isSubmitting,
            errorMessage = errorMessage,
        )
    }

    /**
     * Возвращает профиль пользователя для тестовых auth/session состояний.
     */
    fun authorizedUser(
        id: String = "user-1",
        displayName: String = "Test User",
        username: String? = "test_user",
        photoUrl: String? = null,
        roles: List<String> = listOf("audience", "organizer"),
        activeRole: String? = "audience",
        linkedProviders: List<String> = listOf("password"),
    ): AuthorizedUser {
        return AuthorizedUser(
            id = id,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }

    /**
     * Возвращает рабочее пространство организатора для тестов главного экрана.
     */
    fun workspace(
        id: String = "ws-1",
        name: String = "Moscow Cellar",
        slug: String = "moscow-cellar",
        status: String = "active",
        permissionRole: String = "owner",
        canManageMembers: Boolean = true,
        assignablePermissionRoles: List<String> = listOf("manager", "checker", "host"),
        memberships: List<OrganizerWorkspaceMembership> = listOf(workspaceMembership()),
    ): OrganizerWorkspace {
        return OrganizerWorkspace(
            id = id,
            name = name,
            slug = slug,
            status = status,
            permissionRole = permissionRole,
            canManageMembers = canManageMembers,
            assignablePermissionRoles = assignablePermissionRoles,
            memberships = memberships,
        )
    }

    /**
     * Возвращает membership внутри workspace для тестов главного экрана.
     */
    fun workspaceMembership(
        membershipId: String = "wm-1",
        userId: String = "user-1",
        displayName: String = "Test User",
        username: String? = "test_user",
        permissionRole: String = "owner",
        status: String = "active",
        invitedByDisplayName: String? = null,
        isCurrentUser: Boolean = true,
        canEditRole: Boolean = false,
        assignablePermissionRoles: List<String> = emptyList(),
    ): OrganizerWorkspaceMembership {
        return OrganizerWorkspaceMembership(
            membershipId = membershipId,
            userId = userId,
            displayName = displayName,
            username = username,
            permissionRole = permissionRole,
            status = status,
            invitedByDisplayName = invitedByDisplayName,
            isCurrentUser = isCurrentUser,
            canEditRole = canEditRole,
            assignablePermissionRoles = assignablePermissionRoles,
        )
    }

    /**
     * Возвращает pending invitation текущего пользователя для тестов главного экрана.
     */
    fun workspaceInvitation(
        membershipId: String = "wm-invite-1",
        workspaceId: String = "ws-2",
        workspaceName: String = "Late Night Standup",
        workspaceSlug: String = "late-night-standup",
        workspaceStatus: String = "active",
        permissionRole: String = "checker",
        invitedByDisplayName: String? = "Owner User",
    ): OrganizerWorkspaceInvitation {
        return OrganizerWorkspaceInvitation(
            membershipId = membershipId,
            workspaceId = workspaceId,
            workspaceName = workspaceName,
            workspaceSlug = workspaceSlug,
            workspaceStatus = workspaceStatus,
            permissionRole = permissionRole,
            invitedByDisplayName = invitedByDisplayName,
        )
    }

    /**
     * Возвращает тестовый билет для audience/staff ticketing сценариев.
     */
    fun issuedTicket(
        id: String = "ticket-1",
        orderId: String = "order-1",
        eventId: String = "event-1",
        inventoryUnitId: String = "inventory-1",
        inventoryRef: String = "seat-a1",
        label: String = "Ряд A · Место 1",
        status: IssuedTicketStatus = IssuedTicketStatus.ISSUED,
        qrPayload: String? = "incomedy.ticket.v1:ticket-1",
        issuedAtIso: String = "2026-04-01T18:45:00+03:00",
        checkedInAtIso: String? = null,
        checkedInByUserId: String? = null,
    ): IssuedTicket {
        return IssuedTicket(
            id = id,
            orderId = orderId,
            eventId = eventId,
            inventoryUnitId = inventoryUnitId,
            inventoryRef = inventoryRef,
            label = label,
            status = status,
            qrPayload = qrPayload,
            issuedAtIso = issuedAtIso,
            checkedInAtIso = checkedInAtIso,
            checkedInByUserId = checkedInByUserId,
        )
    }

    /**
     * Возвращает тестовую площадку организатора с одним hall template.
     */
    fun venue(
        id: String = "venue-1",
        workspaceId: String = "ws-1",
        name: String = "Moscow Cellar",
        city: String = "Moscow",
        address: String = "Tverskaya 1",
        timezone: String = "Europe/Moscow",
        capacity: Int = 120,
        description: String? = "Клубный зал для вечерних шоу",
        contacts: List<VenueContact> = listOf(VenueContact(label = "Telegram", value = "@cellar")),
        hallTemplates: List<HallTemplate> = listOf(hallTemplate()),
    ): OrganizerVenue {
        return OrganizerVenue(
            id = id,
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contacts = contacts,
            hallTemplates = hallTemplates,
        )
    }

    /**
     * Возвращает тестовый hall template для builder UI.
     */
    fun hallTemplate(
        id: String = "template-1",
        venueId: String = "venue-1",
        name: String = "Late Layout",
        version: Int = 2,
        status: HallTemplateStatus = HallTemplateStatus.PUBLISHED,
        layout: HallLayout = HallLayout(
            priceZones = listOf(HallPriceZone(id = "vip", name = "VIP", defaultPriceMinor = 3000)),
            rows = listOf(
                HallRow(
                    id = "row-a",
                    label = "A",
                    seats = listOf(
                        HallSeat(ref = "row-a-1", label = "1"),
                        HallSeat(ref = "row-a-2", label = "2"),
                        HallSeat(ref = "row-a-3", label = "3"),
                    ),
                    priceZoneId = "vip",
                ),
            ),
            blockedSeatRefs = listOf("row-a-3"),
        ),
    ): HallTemplate {
        return HallTemplate(
            id = id,
            venueId = venueId,
            name = name,
            version = version,
            status = status,
            layout = layout,
        )
    }

    /**
     * Возвращает тестовый organizer event с frozen hall snapshot.
     */
    fun event(
        id: String = "event-1",
        workspaceId: String = "ws-1",
        venueId: String = "venue-1",
        venueName: String = "Moscow Cellar",
        sourceTemplateId: String = "template-1",
        sourceTemplateName: String = "Late Layout",
        title: String = "Late Night Standup",
        description: String? = "Проверка EventHallSnapshot foundation",
        startsAtIso: String = "2026-04-01T19:00:00+03:00",
        doorsOpenAtIso: String? = "2026-04-01T18:30:00+03:00",
        endsAtIso: String? = "2026-04-01T21:00:00+03:00",
        status: EventStatus = EventStatus.DRAFT,
        salesStatus: EventSalesStatus = EventSalesStatus.CLOSED,
        currency: String = "RUB",
        visibility: EventVisibility = EventVisibility.PUBLIC,
        hallSnapshot: EventHallSnapshot = EventHallSnapshot(
            id = "snapshot-1",
            eventId = id,
            sourceTemplateId = sourceTemplateId,
            layout = hallTemplate(
                id = sourceTemplateId,
                venueId = venueId,
                name = sourceTemplateName,
            ).layout,
        ),
    ): OrganizerEvent {
        return OrganizerEvent(
            id = id,
            workspaceId = workspaceId,
            venueId = venueId,
            venueName = venueName,
            hallSnapshotId = hallSnapshot.id,
            sourceTemplateId = sourceTemplateId,
            sourceTemplateName = sourceTemplateName,
            title = title,
            description = description,
            startsAtIso = startsAtIso,
            doorsOpenAtIso = doorsOpenAtIso,
            endsAtIso = endsAtIso,
            status = status,
            salesStatus = salesStatus,
            currency = currency,
            visibility = visibility,
            hallSnapshot = hallSnapshot,
        )
    }

    /**
     * Возвращает тестовую comedian application для lineup UI.
     */
    fun comedianApplication(
        id: String = "application-1",
        eventId: String = "event-1",
        comedianUserId: String = "comedian-1",
        comedianDisplayName: String = "Иван Смехов",
        comedianUsername: String? = "smile",
        status: ComedianApplicationStatus = ComedianApplicationStatus.SUBMITTED,
        note: String? = "Новый пятиминутный сет",
        reviewedByUserId: String? = null,
        reviewedByDisplayName: String? = null,
        createdAtIso: String = "2026-03-23T01:00:00+03:00",
        updatedAtIso: String = "2026-03-23T01:00:00+03:00",
        statusUpdatedAtIso: String = "2026-03-23T01:00:00+03:00",
    ): ComedianApplication {
        return ComedianApplication(
            id = id,
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianDisplayName,
            comedianUsername = comedianUsername,
            status = status,
            note = note,
            reviewedByUserId = reviewedByUserId,
            reviewedByDisplayName = reviewedByDisplayName,
            createdAtIso = createdAtIso,
            updatedAtIso = updatedAtIso,
            statusUpdatedAtIso = statusUpdatedAtIso,
        )
    }

    /**
     * Возвращает тестовый lineup entry для UI-проверок reorder.
     */
    fun lineupEntry(
        id: String = "entry-1",
        eventId: String = "event-1",
        comedianUserId: String = "comedian-1",
        comedianDisplayName: String = "Иван Смехов",
        comedianUsername: String? = "smile",
        applicationId: String? = "application-1",
        orderIndex: Int = 1,
        status: LineupEntryStatus = LineupEntryStatus.DRAFT,
        notes: String? = null,
        createdAtIso: String = "2026-03-23T01:10:00+03:00",
        updatedAtIso: String = "2026-03-23T01:10:00+03:00",
    ): LineupEntry {
        return LineupEntry(
            id = id,
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianDisplayName,
            comedianUsername = comedianUsername,
            applicationId = applicationId,
            orderIndex = orderIndex,
            status = status,
            notes = notes,
            createdAtIso = createdAtIso,
            updatedAtIso = updatedAtIso,
        )
    }
}
