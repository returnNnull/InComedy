package com.bam.incomedy.server.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация organizer event persistence.
 *
 * Репозиторий изолирует создание/list/publish событий и frozen hall snapshots от venue/workspace
 * persistence, чтобы новый bounded context не расширял существующие repository-классы.
 */
class PostgresEventRepository(
    private val dataSource: DataSource,
) : EventRepository {
    /** Возвращает события пользователя по активным membership и подгружает frozen snapshots. */
    override fun listEvents(userId: String): List<StoredOrganizerEvent> {
        dataSource.connection.use { connection ->
            val sql = """
                SELECT
                    e.id,
                    e.workspace_id,
                    e.venue_id,
                    e.venue_name,
                    e.title,
                    e.description,
                    e.starts_at,
                    e.doors_open_at,
                    e.ends_at,
                    e.status,
                    e.sales_status,
                    e.currency,
                    e.visibility,
                    s.id AS hall_snapshot_id,
                    s.source_template_id,
                    s.source_template_name,
                    s.snapshot_json
                FROM organizer_events e
                JOIN event_hall_snapshots s
                  ON s.event_id = e.id
                JOIN workspace_members wm
                  ON wm.workspace_id = e.workspace_id
                WHERE wm.user_id = ?
                  AND wm.joined_at IS NOT NULL
                ORDER BY e.starts_at, e.created_at
            """.trimIndent()
            return connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeQuery().use(::loadEventList)
            }
        }
    }

    /** Создает organizer event и snapshot в одной транзакции. */
    override fun createEvent(
        workspaceId: String,
        venueId: String,
        venueName: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        status: String,
        salesStatus: String,
        currency: String,
        visibility: String,
        sourceTemplateId: String,
        sourceTemplateName: String,
        snapshotJson: String,
    ): StoredOrganizerEvent {
        val eventId = UUID.randomUUID().toString()
        val snapshotId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    INSERT INTO organizer_events (
                        id,
                        workspace_id,
                        venue_id,
                        venue_name,
                        title,
                        description,
                        starts_at,
                        doors_open_at,
                        ends_at,
                        status,
                        sales_status,
                        currency,
                        visibility,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(eventId))
                    statement.setObject(2, UUID.fromString(workspaceId))
                    statement.setObject(3, UUID.fromString(venueId))
                    statement.setString(4, venueName)
                    statement.setString(5, title)
                    statement.setString(6, description)
                    statement.setObject(7, startsAt)
                    statement.setObject(8, doorsOpenAt)
                    statement.setObject(9, endsAt)
                    statement.setString(10, status)
                    statement.setString(11, salesStatus)
                    statement.setString(12, currency)
                    statement.setString(13, visibility)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO event_hall_snapshots (
                        id,
                        event_id,
                        source_template_id,
                        source_template_name,
                        snapshot_json,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(snapshotId))
                    statement.setObject(2, UUID.fromString(eventId))
                    statement.setObject(3, UUID.fromString(sourceTemplateId))
                    statement.setString(4, sourceTemplateName)
                    statement.setString(5, snapshotJson)
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
            return requireNotNull(loadEvent(connection, eventId))
        }
    }

    /** Возвращает одно событие вместе со snapshot. */
    override fun findEvent(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return loadEvent(connection, eventId)
        }
    }

    /** Публикует draft-событие и возвращает его текущее состояние. */
    override fun publishEvent(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE organizer_events
                SET
                    status = 'published',
                    updated_at = NOW()
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(eventId))
                if (statement.executeUpdate() == 0) return null
            }
            return loadEvent(connection, eventId)
        }
    }

    /** Загружает одно событие вместе со snapshot по его id. */
    private fun loadEvent(
        connection: Connection,
        eventId: String,
    ): StoredOrganizerEvent? {
        val sql = """
            SELECT
                e.id,
                e.workspace_id,
                e.venue_id,
                e.venue_name,
                e.title,
                e.description,
                e.starts_at,
                e.doors_open_at,
                e.ends_at,
                e.status,
                e.sales_status,
                e.currency,
                e.visibility,
                s.id AS hall_snapshot_id,
                s.source_template_id,
                s.source_template_name,
                s.snapshot_json
            FROM organizer_events e
            JOIN event_hall_snapshots s
              ON s.event_id = e.id
            WHERE e.id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredOrganizerEvent()
            }
        }
    }

    /** Преобразует result set списка в read-only stored модели. */
    private fun loadEventList(result: ResultSet): List<StoredOrganizerEvent> {
        return buildList {
            while (result.next()) {
                add(result.toStoredOrganizerEvent())
            }
        }
    }

    /** Маппит текущую строку result set в stored organizer event. */
    private fun ResultSet.toStoredOrganizerEvent(): StoredOrganizerEvent {
        val eventId = getObject("id").toString()
        return StoredOrganizerEvent(
            id = eventId,
            workspaceId = getObject("workspace_id").toString(),
            venueId = getObject("venue_id").toString(),
            venueName = getString("venue_name"),
            title = getString("title"),
            description = getString("description"),
            startsAt = getObject("starts_at", OffsetDateTime::class.java),
            doorsOpenAt = getObject("doors_open_at", OffsetDateTime::class.java),
            endsAt = getObject("ends_at", OffsetDateTime::class.java),
            status = getString("status"),
            salesStatus = getString("sales_status"),
            currency = getString("currency"),
            visibility = getString("visibility"),
            hallSnapshot = StoredEventHallSnapshot(
                id = getObject("hall_snapshot_id").toString(),
                eventId = eventId,
                sourceTemplateId = getObject("source_template_id").toString(),
                sourceTemplateName = getString("source_template_name"),
                snapshotJson = getString("snapshot_json"),
            ),
        )
    }
}
