package com.bam.incomedy.server.notifications

import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
import com.bam.incomedy.server.db.AnnouncementRepository
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.StoredEventAnnouncement
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository

/**
 * Backend orchestration первого notifications slice-а с organizer announcements/event feed.
 *
 * Сервис держит отдельно public-availability rules, RBAC публикации и нормализацию audience-safe
 * author role, чтобы HTTP-роуты не дублировали policy поверх persistence.
 */
class AnnouncementService(
    private val workspaceRepository: WorkspaceRepository,
    private val eventRepository: EventRepository,
    private val announcementRepository: AnnouncementRepository,
) {
    /** Возвращает публичный feed announcement-ов опубликованного public-события. */
    fun listPublicEventAnnouncements(
        eventId: String,
    ): List<EventAnnouncement> {
        val event = eventRepository.findEvent(eventId) ?: throw AnnouncementEventNotFoundException(eventId)
        if (!event.isPublicAnnouncementFeedAvailable()) {
            throw AnnouncementFeedUnavailableException(eventId)
        }
        return announcementRepository.listEventAnnouncements(eventId).map { announcement ->
            announcement.toDomain()
        }
    }

    /** Публикует organizer/host announcement для уже доступного audience feed-а. */
    fun createEventAnnouncement(
        actorUserId: String,
        eventId: String,
        message: String,
    ): EventAnnouncement {
        val event = eventRepository.findEvent(eventId) ?: throw AnnouncementEventNotFoundException(eventId)
        if (!event.isPublicAnnouncementFeedAvailable()) {
            throw AnnouncementFeedUnavailableException(eventId)
        }
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = event.workspaceId,
            userId = actorUserId,
        ) ?: throw AnnouncementScopeNotFoundException(event.workspaceId)
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            throw AnnouncementValidationException("Announcement message must not be blank")
        }
        if (normalizedMessage.length > 1000) {
            throw AnnouncementValidationException("Announcement message must be at most 1000 characters")
        }

        val authorRole = when (access.permissionRole) {
            WorkspacePermissionRole.OWNER,
            WorkspacePermissionRole.MANAGER -> EventAnnouncementAuthorRole.ORGANIZER

            WorkspacePermissionRole.HOST -> EventAnnouncementAuthorRole.HOST
            WorkspacePermissionRole.CHECKER -> throw AnnouncementPermissionDeniedException("announcement_manage_forbidden")
        }
        return announcementRepository.createEventAnnouncement(
            eventId = eventId,
            createdByUserId = actorUserId,
            authorRole = authorRole.wireName,
            message = normalizedMessage,
        ).toDomain()
    }

    /** Проверяет, что event уже опубликован и доступен audience feed-у. */
    private fun StoredOrganizerEvent.isPublicAnnouncementFeedAvailable(): Boolean {
        return status == EventStatus.PUBLISHED.wireName &&
            visibility == EventVisibility.PUBLIC.wireName
    }

    /** Преобразует stored announcement в domain-модель для public API и websocket-а. */
    private fun StoredEventAnnouncement.toDomain(): EventAnnouncement {
        return EventAnnouncement(
            id = id,
            eventId = eventId,
            message = message,
            authorRole = requireNotNull(EventAnnouncementAuthorRole.fromWireName(authorRole)) {
                "Unknown persisted author role: $authorRole"
            },
            createdAtIso = createdAt.toString(),
        )
    }
}

/**
 * Сигнализирует, что event id не найден в persistence.
 *
 * @property eventId Идентификатор отсутствующего события.
 */
class AnnouncementEventNotFoundException(
    val eventId: String,
) : IllegalArgumentException("event_not_found")

/**
 * Сигнализирует, что actor не видит нужный workspace scope.
 *
 * @property workspaceId Идентификатор отсутствующего workspace scope.
 */
class AnnouncementScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("workspace_scope_not_found")

/**
 * Сигнализирует, что public feed недоступен для выбранного события.
 *
 * @property eventId Идентификатор события с недоступным feed-ом.
 */
class AnnouncementFeedUnavailableException(
    val eventId: String,
) : IllegalStateException("announcement_feed_unavailable")

/**
 * Сигнализирует о запрете публикации announcement-а для текущей роли.
 *
 * @property safeCode Стабильный код permission error.
 */
class AnnouncementPermissionDeniedException(
    val safeCode: String,
) : IllegalAccessException(safeCode)

/**
 * Сигнализирует о некорректном request payload или lifecycle state announcement-а.
 *
 * @property safeMessage Безопасное сообщение для API response.
 */
class AnnouncementValidationException(
    val safeMessage: String,
) : IllegalArgumentException(safeMessage)
