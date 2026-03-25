package com.bam.incomedy.data.lineup.backend

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryOrderUpdate
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.domain.lineup.LineupLiveUpdate
import com.bam.incomedy.domain.lineup.LineupManagementService
import kotlinx.coroutines.flow.Flow

/**
 * Backend-реализация `LineupManagementService`.
 *
 * Адаптер оставляет transport-детали внутри `LineupBackendApi`, а shared/domain/feature слои
 * получают только стабильные модели bounded context-а `lineup`.
 */
class BackendLineupManagementService(
    private val lineupBackendApi: LineupBackendApi,
) : LineupManagementService {
    /** Отправляет comedian application через backend API. */
    override suspend fun submitApplication(
        accessToken: String,
        eventId: String,
        note: String?,
    ): Result<ComedianApplication> {
        return lineupBackendApi.submitApplication(
            accessToken = accessToken,
            eventId = eventId,
            note = note,
        )
    }

    /** Загружает organizer список заявок через backend API. */
    override suspend fun listEventApplications(
        accessToken: String,
        eventId: String,
    ): Result<List<ComedianApplication>> {
        return lineupBackendApi.listEventApplications(
            accessToken = accessToken,
            eventId = eventId,
        )
    }

    /** Меняет review-статус заявки через backend API. */
    override suspend fun updateApplicationStatus(
        accessToken: String,
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ): Result<ComedianApplication> {
        return lineupBackendApi.updateApplicationStatus(
            accessToken = accessToken,
            eventId = eventId,
            applicationId = applicationId,
            status = status,
        )
    }

    /** Загружает lineup события через backend API. */
    override suspend fun listEventLineup(
        accessToken: String,
        eventId: String,
    ): Result<List<LineupEntry>> {
        return lineupBackendApi.listEventLineup(
            accessToken = accessToken,
            eventId = eventId,
        )
    }

    /** Выполняет reorder lineup через backend API. */
    override suspend fun reorderLineup(
        accessToken: String,
        eventId: String,
        entries: List<LineupEntryOrderUpdate>,
    ): Result<List<LineupEntry>> {
        return lineupBackendApi.reorderLineup(
            accessToken = accessToken,
            eventId = eventId,
            entries = entries,
        )
    }

    /** Меняет live-stage статус lineup entry через backend API. */
    override suspend fun updateLineupEntryStatus(
        accessToken: String,
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ): Result<List<LineupEntry>> {
        return lineupBackendApi.updateLineupEntryStatus(
            accessToken = accessToken,
            eventId = eventId,
            entryId = entryId,
            status = status,
        )
    }

    /** Подписывает consumer-ов на public live-event updates опубликованного события. */
    override fun observeEventLiveUpdates(eventId: String): Flow<LineupLiveUpdate> {
        return lineupBackendApi.observeEventLiveUpdates(eventId = eventId)
    }
}
