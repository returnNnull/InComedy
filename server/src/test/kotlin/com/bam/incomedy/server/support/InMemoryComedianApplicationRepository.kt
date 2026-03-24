package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.ComedianApplicationRepository
import com.bam.incomedy.server.db.ComedianApplicationStatus
import com.bam.incomedy.server.db.StoredComedianApplication
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация repository для route/service тестов заявок комиков.
 *
 * Хранилище дает детерминированный backend read/write path без настоящей БД, чтобы проверять
 * submit/list/status change сценарии изолированно.
 */
class InMemoryComedianApplicationRepository(
    private val nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
) : ComedianApplicationRepository {
    /** Mutable заявки по их id. */
    private val recordsById = linkedMapOf<String, MutableComedianApplicationRecord>()

    override fun listEventApplications(eventId: String): List<StoredComedianApplication> {
        return recordsById.values
            .filter { it.eventId == eventId }
            .sortedBy { it.createdAt }
            .map { it.toStored() }
    }

    override fun findEventApplication(
        eventId: String,
        applicationId: String,
    ): StoredComedianApplication? {
        val record = recordsById[applicationId] ?: return null
        if (record.eventId != eventId) return null
        return record.toStored()
    }

    override fun findComedianApplication(
        eventId: String,
        comedianUserId: String,
    ): StoredComedianApplication? {
        return recordsById.values.firstOrNull {
            it.eventId == eventId && it.comedianUserId == comedianUserId
        }?.toStored()
    }

    override fun createComedianApplication(
        eventId: String,
        comedianUserId: String,
        note: String?,
        status: ComedianApplicationStatus,
    ): StoredComedianApplication {
        val timestamp = nowProvider()
        val record = MutableComedianApplicationRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianUserId,
            comedianUsername = null,
            status = status,
            note = note,
            reviewedByUserId = null,
            reviewedByDisplayName = null,
            createdAt = timestamp,
            updatedAt = timestamp,
            statusUpdatedAt = timestamp,
        )
        recordsById[record.id] = record
        return record.toStored()
    }

    override fun updateComedianApplicationStatus(
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
        reviewedByUserId: String,
    ): StoredComedianApplication? {
        val record = recordsById[applicationId] ?: return null
        if (record.eventId != eventId) return null
        val timestamp = nowProvider()
        record.status = status
        record.reviewedByUserId = reviewedByUserId
        record.reviewedByDisplayName = reviewedByUserId
        record.updatedAt = timestamp
        record.statusUpdatedAt = timestamp
        return record.toStored()
    }

    /**
     * Обогащает in-memory запись display metadata по тестовому пользователю.
     *
     * Этот helper нужен тестам, где user profiles уже известны, а repository должен возвращать
     * organizer-friendly read model, а не только ids.
     */
    fun bindUser(
        userId: String,
        displayName: String,
        username: String?,
    ) {
        recordsById.values.forEach { record ->
            if (record.comedianUserId == userId) {
                record.comedianDisplayName = displayName
                record.comedianUsername = username
            }
            if (record.reviewedByUserId == userId) {
                record.reviewedByDisplayName = displayName
            }
        }
    }

    /** Mutable запись заявки комика. */
    private data class MutableComedianApplicationRecord(
        val id: String,
        val eventId: String,
        val comedianUserId: String,
        var comedianDisplayName: String,
        var comedianUsername: String?,
        var status: ComedianApplicationStatus,
        var note: String?,
        var reviewedByUserId: String?,
        var reviewedByDisplayName: String?,
        val createdAt: OffsetDateTime,
        var updatedAt: OffsetDateTime,
        var statusUpdatedAt: OffsetDateTime,
    ) {
        /** Экспортирует mutable запись в immutable response model. */
        fun toStored(): StoredComedianApplication {
            return StoredComedianApplication(
                id = id,
                eventId = eventId,
                comedianUserId = comedianUserId,
                comedianDisplayName = comedianDisplayName,
                comedianUsername = comedianUsername,
                status = status,
                note = note,
                reviewedByUserId = reviewedByUserId,
                reviewedByDisplayName = reviewedByDisplayName,
                createdAt = createdAt,
                updatedAt = updatedAt,
                statusUpdatedAt = statusUpdatedAt,
            )
        }
    }
}
