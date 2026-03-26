package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.AnnouncementRepository
import com.bam.incomedy.server.db.StoredEventAnnouncement
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация announcement feed repository для route/service тестов.
 *
 * Хранилище дает детерминированный порядок feed entries и позволяет проверять public/protected
 * announcement flow без реальной БД.
 */
class InMemoryAnnouncementRepository(
    private val nowProvider: () -> OffsetDateTime = { OffsetDateTime.now() },
) : AnnouncementRepository {
    /** Mutable announcement records по их id. */
    private val recordsById = linkedMapOf<String, MutableAnnouncementRecord>()

    /** Возвращает feed события от новых записей к старым. */
    override fun listEventAnnouncements(eventId: String): List<StoredEventAnnouncement> {
        return recordsById.values
            .filter { it.eventId == eventId }
            .sortedWith(compareByDescending<MutableAnnouncementRecord> { it.createdAt }.thenByDescending { it.id })
            .map(MutableAnnouncementRecord::toStored)
    }

    /** Создает новое announcement внутри feed выбранного события. */
    override fun createEventAnnouncement(
        eventId: String,
        createdByUserId: String,
        authorRole: String,
        message: String,
    ): StoredEventAnnouncement {
        val record = MutableAnnouncementRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            createdByUserId = createdByUserId,
            authorRole = authorRole,
            message = message,
            createdAt = nowProvider(),
        )
        recordsById[record.id] = record
        return record.toStored()
    }

    /** Mutable запись announcement-а в in-memory feed-е. */
    private data class MutableAnnouncementRecord(
        val id: String,
        val eventId: String,
        val createdByUserId: String,
        val authorRole: String,
        val message: String,
        val createdAt: OffsetDateTime,
    ) {
        /** Экспортирует mutable запись в immutable stored-модель. */
        fun toStored(): StoredEventAnnouncement {
            return StoredEventAnnouncement(
                id = id,
                eventId = eventId,
                createdByUserId = createdByUserId,
                authorRole = authorRole,
                message = message,
                createdAt = createdAt,
            )
        }
    }
}
