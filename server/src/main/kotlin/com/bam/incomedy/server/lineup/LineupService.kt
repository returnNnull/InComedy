package com.bam.incomedy.server.lineup

import com.bam.incomedy.server.db.ComedianApplicationStatus
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupEntryOrderUpdate
import com.bam.incomedy.server.db.LineupEntryStatus
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.StoredComedianApplication
import com.bam.incomedy.server.db.StoredLineupEntry
import com.bam.incomedy.server.db.WorkspacePermissionRole
import com.bam.incomedy.server.db.WorkspaceRepository

/**
 * Backend orchestration для lineup foundation slice-а.
 *
 * Сервис отвечает за idempotent bridge `approved application -> draft lineup entry`, organizer/host
 * access checks и валидацию reorder payload до обращения к persistence.
 */
class LineupService(
    private val workspaceRepository: WorkspaceRepository,
    private val eventRepository: EventRepository,
    private val lineupRepository: LineupRepository,
) {
    /** Гарантирует, что approved-заявка материализована в draft lineup entry ровно один раз. */
    fun ensureApprovedApplicationEntry(application: StoredComedianApplication): StoredLineupEntry? {
        if (application.status != ComedianApplicationStatus.APPROVED) {
            return null
        }
        val existing = lineupRepository.findApplicationLineupEntry(
            eventId = application.eventId,
            applicationId = application.id,
        )
        if (existing != null) {
            return existing
        }
        return lineupRepository.createLineupEntry(
            eventId = application.eventId,
            comedianUserId = application.comedianUserId,
            applicationId = application.id,
            status = LineupEntryStatus.DRAFT,
            notes = null,
        )
    }

    /** Возвращает organizer/host-доступный lineup конкретного события. */
    fun listEventLineup(
        actorUserId: String,
        eventId: String,
    ): List<StoredLineupEntry> {
        val event = eventRepository.findEvent(eventId) ?: throw LineupEventNotFoundException(eventId)
        requireManageLineupAccess(
            actorUserId = actorUserId,
            workspaceId = event.workspaceId,
        )
        return lineupRepository.listEventLineup(eventId)
    }

    /** Переставляет lineup события после полной валидации request payload. */
    fun reorderEventLineup(
        actorUserId: String,
        eventId: String,
        updates: List<LineupEntryOrderUpdate>,
    ): List<StoredLineupEntry> {
        val event = eventRepository.findEvent(eventId) ?: throw LineupEventNotFoundException(eventId)
        requireManageLineupAccess(
            actorUserId = actorUserId,
            workspaceId = event.workspaceId,
        )
        val existing = lineupRepository.listEventLineup(eventId)
        validateReorderRequest(
            existing = existing,
            updates = updates,
        )
        return lineupRepository.reorderEventLineup(
            eventId = eventId,
            updates = updates.sortedBy(LineupEntryOrderUpdate::orderIndex),
        )
    }

    /** Проверяет доступ owner/manager/host к organizer lineup surface. */
    private fun requireManageLineupAccess(
        actorUserId: String,
        workspaceId: String,
    ) {
        val access = workspaceRepository.findWorkspaceAccess(
            workspaceId = workspaceId,
            userId = actorUserId,
        ) ?: throw LineupScopeNotFoundException(workspaceId)
        if (access.permissionRole != WorkspacePermissionRole.OWNER &&
            access.permissionRole != WorkspacePermissionRole.MANAGER &&
            access.permissionRole != WorkspacePermissionRole.HOST
        ) {
            throw LineupPermissionDeniedException("lineup_manage_forbidden")
        }
    }

    /** Проверяет, что reorder request покрывает весь lineup и не ломает явный порядок. */
    private fun validateReorderRequest(
        existing: List<StoredLineupEntry>,
        updates: List<LineupEntryOrderUpdate>,
    ) {
        if (updates.isEmpty()) {
            throw LineupValidationException("Lineup reorder request must include at least one entry")
        }
        if (updates.size != existing.size) {
            throw LineupValidationException("Lineup reorder must include the full current lineup")
        }
        val requestIds = updates.map(LineupEntryOrderUpdate::entryId)
        if (requestIds.toSet().size != requestIds.size) {
            throw LineupValidationException("Lineup reorder request contains duplicate entry ids")
        }
        val orderIndexes = updates.map(LineupEntryOrderUpdate::orderIndex)
        if (orderIndexes.any { it <= 0 }) {
            throw LineupValidationException("Lineup order index must be positive")
        }
        if (orderIndexes.toSet().size != orderIndexes.size) {
            throw LineupValidationException("Lineup reorder request contains duplicate order indexes")
        }
        val expectedOrderIndexes = (1..existing.size).toSet()
        if (orderIndexes.toSet() != expectedOrderIndexes) {
            throw LineupValidationException("Lineup reorder request must contain contiguous order indexes")
        }
        val existingIds = existing.map(StoredLineupEntry::id).toSet()
        if (requestIds.toSet() != existingIds) {
            throw LineupValidationException("Lineup reorder request must reference the current lineup entries exactly")
        }
    }
}

/** Ошибка отсутствующего события для lineup slice-а. */
class LineupEventNotFoundException(
    val eventId: String,
) : IllegalStateException("Event was not found for lineup")

/** Ошибка отсутствующего organizer workspace scope для lineup slice-а. */
class LineupScopeNotFoundException(
    val workspaceId: String,
) : IllegalStateException("Workspace scope was not found for lineup")

/** Ошибка прав доступа organizer lineup surface-а. */
class LineupPermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Lineup action is forbidden")

/** Ошибка валидации lineup reorder flow. */
class LineupValidationException(
    override val message: String,
) : IllegalArgumentException(message)
