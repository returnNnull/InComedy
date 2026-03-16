package com.bam.incomedy.testsupport

import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.AuthorizedUser
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
}
