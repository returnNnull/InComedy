package com.bam.incomedy.server.lineup

import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.server.db.ComedianApplicationRepository
import com.bam.incomedy.server.db.ComedianApplicationStatus
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.StoredComedianApplication
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository

/**
 * Backend orchestration для `comedian applications`.
 *
 * Сервис держит domain policy отдельно от HTTP-роутов: проверяет event/workspace scope, роль
 * комика, допустимые review transition-ы и ограничения первого backend-only slice.
 */
class ComedianApplicationsService(
    private val sessionUserRepository: SessionUserRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val eventRepository: EventRepository,
    private val comedianApplicationRepository: ComedianApplicationRepository,
    private val lineupRepository: LineupRepository,
) {
    /** Создает новую заявку комика на опубликованное событие. */
    fun submitApplication(
        actorUserId: String,
        eventId: String,
        note: String?,
    ): StoredComedianApplication {
        val actor = sessionUserRepository.findById(actorUserId)
            ?: throw ComedianApplicationPermissionDeniedException("application_submit_forbidden")
        if (UserRole.COMEDIAN !in actor.roles) {
            throw ComedianApplicationPermissionDeniedException("comedian_role_required")
        }
        val event = eventRepository.findEvent(eventId) ?: throw ComedianApplicationEventNotFoundException(eventId)
        if (event.status != EventStatus.PUBLISHED.wireName) {
            throw ComedianApplicationValidationException("Подать заявку можно только на опубликованное событие")
        }
        if (comedianApplicationRepository.findComedianApplication(eventId, actorUserId) != null) {
            throw ComedianApplicationConflictException("application_already_exists")
        }
        return comedianApplicationRepository.createComedianApplication(
            eventId = eventId,
            comedianUserId = actorUserId,
            note = note,
            status = ComedianApplicationStatus.SUBMITTED,
        )
    }

    /** Возвращает organizer список заявок события после owner/manager access check. */
    fun listEventApplications(
        actorUserId: String,
        eventId: String,
    ): List<StoredComedianApplication> {
        val event = eventRepository.findEvent(eventId) ?: throw ComedianApplicationEventNotFoundException(eventId)
        requireManageApplicationsAccess(
            actorUserId = actorUserId,
            workspaceId = event.workspaceId,
        )
        return comedianApplicationRepository.listEventApplications(eventId)
    }

    /** Меняет review-статус заявки organizer-ом и при `approved` материализует draft lineup entry. */
    fun updateApplicationStatus(
        actorUserId: String,
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ): StoredComedianApplication {
        val event = eventRepository.findEvent(eventId) ?: throw ComedianApplicationEventNotFoundException(eventId)
        requireManageApplicationsAccess(
            actorUserId = actorUserId,
            workspaceId = event.workspaceId,
        )
        requireReviewStatus(status)
        val existing = comedianApplicationRepository.findEventApplication(
            eventId = eventId,
            applicationId = applicationId,
        ) ?: throw ComedianApplicationNotFoundException(applicationId)
        if (existing.status == ComedianApplicationStatus.WITHDRAWN) {
            throw ComedianApplicationValidationException("Снятая заявка не может быть возвращена в review-поток")
        }
        val updated = comedianApplicationRepository.updateComedianApplicationStatus(
            eventId = eventId,
            applicationId = applicationId,
            status = status,
            reviewedByUserId = actorUserId,
        ) ?: throw ComedianApplicationNotFoundException(applicationId)
        LineupService(
            workspaceRepository = workspaceRepository,
            eventRepository = eventRepository,
            lineupRepository = lineupRepository,
        ).ensureApprovedApplicationEntry(updated)
        return updated
    }

    /** Проверяет owner/manager доступ к organizer workspace события. */
    private fun requireManageApplicationsAccess(
        actorUserId: String,
        workspaceId: String,
    ) {
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw ComedianApplicationScopeNotFoundException(workspaceId)
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER
        ) {
            throw ComedianApplicationPermissionDeniedException("application_review_forbidden")
        }
    }

    /** Ограничивает первый backend slice review-статусами organizer decision surface. */
    private fun requireReviewStatus(status: ComedianApplicationStatus) {
        if (status != ComedianApplicationStatus.SHORTLISTED &&
            status != ComedianApplicationStatus.APPROVED &&
            status != ComedianApplicationStatus.WAITLISTED &&
            status != ComedianApplicationStatus.REJECTED
        ) {
            throw ComedianApplicationValidationException("Для organizer review доступны только shortlisted/approved/waitlisted/rejected")
        }
    }
}

/** Ошибка отсутствующего события для applications slice-а. */
class ComedianApplicationEventNotFoundException(
    val eventId: String,
) : IllegalStateException("Event was not found for comedian applications")

/** Ошибка отсутствующего organizer workspace scope. */
class ComedianApplicationScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("Workspace scope was not found for comedian applications")

/** Ошибка отсутствующей заявки. */
class ComedianApplicationNotFoundException(
    val applicationId: String,
) : IllegalStateException("Comedian application was not found")

/** Ошибка прав доступа applications slice-а. */
class ComedianApplicationPermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Comedian application action is forbidden")

/** Ошибка валидации запроса/transition-а applications slice-а. */
class ComedianApplicationValidationException(
    override val message: String,
) : IllegalArgumentException(message)

/** Ошибка конфликта состояния applications slice-а. */
class ComedianApplicationConflictException(
    val reasonCode: String,
) : IllegalStateException("Comedian application state conflict")
