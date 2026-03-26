package com.bam.incomedy.server.db

import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация event announcement feed persistence.
 *
 * Репозиторий изолирует хранение публичного feed-а события от `organizer_events`, чтобы
 * notifications foundation не превращался в набор ad-hoc полей внутри event aggregate.
 */
class PostgresAnnouncementRepository(
    private val dataSource: DataSource,
) : AnnouncementRepository {
    /** Возвращает announcement-ы события в обратном хронологическом порядке. */
    override fun listEventAnnouncements(eventId: String): List<StoredEventAnnouncement> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, event_id, created_by_user_id, author_role, message, created_at
                FROM event_announcements
                WHERE event_id = ?
                ORDER BY created_at DESC, id DESC
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.executeQuery().use { result ->
                    return buildList {
                        while (result.next()) {
                            add(result.toStoredEventAnnouncement())
                        }
                    }
                }
            }
        }
    }

    /** Создает новое audience-safe announcement для выбранного события. */
    override fun createEventAnnouncement(
        eventId: String,
        createdByUserId: String,
        authorRole: String,
        message: String,
    ): StoredEventAnnouncement {
        val id = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO event_announcements (
                    id,
                    event_id,
                    created_by_user_id,
                    author_role,
                    message,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, NOW())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(id))
                statement.setObject(2, UUID.fromString(eventId))
                statement.setObject(3, UUID.fromString(createdByUserId))
                statement.setString(4, authorRole)
                statement.setString(5, message)
                statement.executeUpdate()
            }
            return requireNotNull(loadAnnouncement(connection = connection, announcementId = id))
        }
    }

    /** Загружает одно announcement-сообщение по id. */
    private fun loadAnnouncement(
        connection: java.sql.Connection,
        announcementId: String,
    ): StoredEventAnnouncement? {
        connection.prepareStatement(
            """
            SELECT id, event_id, created_by_user_id, author_role, message, created_at
            FROM event_announcements
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(announcementId))
            statement.executeQuery().use { result ->
                return if (result.next()) result.toStoredEventAnnouncement() else null
            }
        }
    }

    /** Маппит SQL-row в immutable stored announcement-модель. */
    private fun ResultSet.toStoredEventAnnouncement(): StoredEventAnnouncement {
        return StoredEventAnnouncement(
            id = getObject("id", UUID::class.java).toString(),
            eventId = getObject("event_id", UUID::class.java).toString(),
            createdByUserId = getObject("created_by_user_id", UUID::class.java).toString(),
            authorRole = getString("author_role"),
            message = getString("message"),
            createdAt = getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
