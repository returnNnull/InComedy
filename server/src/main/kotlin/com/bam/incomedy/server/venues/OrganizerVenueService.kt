package com.bam.incomedy.server.venues

import com.bam.incomedy.server.db.StoredHallTemplate
import com.bam.incomedy.server.db.StoredVenue
import com.bam.incomedy.server.db.VenueRepository
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository

/**
 * Сервис organizer venue management bounded context-а.
 *
 * Он отделяет permission policy и scope validation от HTTP-роутов, чтобы создание площадок и
 * hall template mutations не дублировали access checks по owner/manager workspace roles.
 *
 * @property workspaceRepository Репозиторий organizer workspace access policy.
 * @property venueRepository Репозиторий площадок и hall templates.
 */
class OrganizerVenueService(
    private val workspaceRepository: WorkspaceRepository,
    private val venueRepository: VenueRepository,
) {
    /** Возвращает площадки, доступные текущему пользователю по активным workspace memberships. */
    fun listVenues(userId: String): List<StoredVenue> = venueRepository.listVenues(userId)

    /** Создает площадку после проверки доступа owner/manager к workspace. */
    fun createVenue(
        actorUserId: String,
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsJson: String,
    ): StoredVenue {
        requireManageVenueAccess(actorUserId, workspaceId)
        return venueRepository.createVenue(
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contactsJson = contactsJson,
        )
    }

    /** Создает hall template внутри площадки после проверки owner/manager доступа. */
    fun createHallTemplate(
        actorUserId: String,
        venueId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate {
        val venue = venueRepository.findVenue(venueId) ?: throw VenueNotFoundException(venueId)
        requireManageVenueAccess(actorUserId, venue.workspaceId)
        return venueRepository.createHallTemplate(
            venueId = venueId,
            name = name,
            status = status,
            layoutJson = layoutJson,
        )
    }

    /** Обновляет hall template и инкрементирует его версию после проверки owner/manager доступа. */
    fun updateHallTemplate(
        actorUserId: String,
        templateId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate {
        val template = venueRepository.findHallTemplate(templateId) ?: throw HallTemplateNotFoundException(templateId)
        val venue = venueRepository.findVenue(template.venueId) ?: throw VenueNotFoundException(template.venueId)
        requireManageVenueAccess(actorUserId, venue.workspaceId)
        return venueRepository.updateHallTemplate(
            templateId = templateId,
            name = name,
            status = status,
            layoutJson = layoutJson,
        ) ?: throw HallTemplateNotFoundException(templateId)
    }

    /** Клонирует hall template в той же площадке с owner/manager permission check. */
    fun cloneHallTemplate(
        actorUserId: String,
        templateId: String,
        clonedName: String?,
        clonedStatus: String,
    ): StoredHallTemplate {
        val template = venueRepository.findHallTemplate(templateId) ?: throw HallTemplateNotFoundException(templateId)
        val venue = venueRepository.findVenue(template.venueId) ?: throw VenueNotFoundException(template.venueId)
        requireManageVenueAccess(actorUserId, venue.workspaceId)
        return venueRepository.cloneHallTemplate(
            sourceTemplateId = templateId,
            name = clonedName?.takeIf(String::isNotBlank) ?: "${template.name} Copy",
            status = clonedStatus,
        ) ?: throw HallTemplateNotFoundException(templateId)
    }

    /** Проверяет, что actor имеет owner/manager доступ к workspace площадки. */
    private fun requireManageVenueAccess(
        actorUserId: String,
        workspaceId: String,
    ) {
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw VenueScopeNotFoundException(workspaceId)
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER
        ) {
            throw VenuePermissionDeniedException("venue_manage_forbidden")
        }
    }
}

/** Сигнализирует, что площадка недоступна в текущем scope. */
class VenueNotFoundException(
    val venueId: String,
) : IllegalStateException("Venue was not found")

/** Сигнализирует, что hall template недоступен в текущем scope. */
class HallTemplateNotFoundException(
    val templateId: String,
) : IllegalStateException("Hall template was not found")

/** Сигнализирует, что пользователь не имеет доступа к workspace площадки. */
class VenueScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("Venue workspace scope was not found")

/** Сигнализирует о запрете действия в venue bounded context-е. */
class VenuePermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Venue action is forbidden")
