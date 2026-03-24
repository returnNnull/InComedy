package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.LineupEntryOrderUpdate
import com.bam.incomedy.server.db.LineupEntryStatus
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.StoredLineupEntry
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация repository для route/service тестов lineup slice-а.
 *
 * Хранилище дает детерминированную перестановку `order_index` и idempotent create path без реальной
 * БД, чтобы безопасно проверять foundation behavior.
 */
class InMemoryLineupRepository(
    private val nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
) : LineupRepository {
    /** Mutable lineup entries по их id. */
    private val recordsById = linkedMapOf<String, MutableLineupEntryRecord>()

    override fun listEventLineup(eventId: String): List<StoredLineupEntry> {
        return recordsById.values
            .filter { it.eventId == eventId }
            .sortedWith(compareBy<MutableLineupEntryRecord> { it.orderIndex }.thenBy { it.id })
            .map { it.toStored() }
    }

    override fun findApplicationLineupEntry(
        eventId: String,
        applicationId: String,
    ): StoredLineupEntry? {
        return recordsById.values.firstOrNull {
            it.eventId == eventId && it.applicationId == applicationId
        }?.toStored()
    }

    override fun createLineupEntry(
        eventId: String,
        comedianUserId: String,
        applicationId: String?,
        status: LineupEntryStatus,
        notes: String?,
    ): StoredLineupEntry {
        val timestamp = nowProvider()
        val nextOrderIndex = recordsById.values
            .filter { it.eventId == eventId }
            .maxOfOrNull { it.orderIndex }
            ?.plus(1)
            ?: 1
        val record = MutableLineupEntryRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            comedianUserId = comedianUserId,
            comedianDisplayName = comedianUserId,
            comedianUsername = null,
            applicationId = applicationId,
            orderIndex = nextOrderIndex,
            status = status,
            notes = notes,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        recordsById[record.id] = record
        return record.toStored()
    }

    override fun updateLineupEntryStatus(
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ): List<StoredLineupEntry> {
        val timestamp = nowProvider()
        val record = recordsById[entryId]
        if (record != null && record.eventId == eventId) {
            record.status = status
            record.updatedAt = timestamp
        }
        return listEventLineup(eventId)
    }

    override fun reorderEventLineup(
        eventId: String,
        updates: List<LineupEntryOrderUpdate>,
    ): List<StoredLineupEntry> {
        val timestamp = nowProvider()
        updates.forEach { update ->
            val record = recordsById[update.entryId] ?: return@forEach
            if (record.eventId == eventId) {
                record.orderIndex = update.orderIndex
                record.updatedAt = timestamp
            }
        }
        return listEventLineup(eventId)
    }

    /**
     * Обогащает in-memory lineup запись display metadata по тестовому пользователю.
     *
     * Этот helper нужен route-тестам, где сервис должен возвращать organizer-friendly read model.
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
        }
    }

    /** Mutable запись lineup entry. */
    private data class MutableLineupEntryRecord(
        val id: String,
        val eventId: String,
        val comedianUserId: String,
        var comedianDisplayName: String,
        var comedianUsername: String?,
        val applicationId: String?,
        var orderIndex: Int,
        var status: LineupEntryStatus,
        var notes: String?,
        val createdAt: OffsetDateTime,
        var updatedAt: OffsetDateTime,
    ) {
        /** Экспортирует mutable запись в immutable response model. */
        fun toStored(): StoredLineupEntry {
            return StoredLineupEntry(
                id = id,
                eventId = eventId,
                comedianUserId = comedianUserId,
                comedianDisplayName = comedianDisplayName,
                comedianUsername = comedianUsername,
                applicationId = applicationId,
                orderIndex = orderIndex,
                status = status,
                notes = notes,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
    }
}
