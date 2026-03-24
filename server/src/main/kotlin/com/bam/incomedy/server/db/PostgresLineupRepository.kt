package com.bam.incomedy.server.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация persistence для lineup entries.
 *
 * Репозиторий держит атомарное создание draft slot-а и перестановку `order_index`, чтобы service
 * слой оперировал доменными инвариантами, а не SQL-деталями.
 */
class PostgresLineupRepository(
    private val dataSource: DataSource,
) : LineupRepository {
    /** Возвращает текущий lineup события в явном порядке выступлений. */
    override fun listEventLineup(eventId: String): List<StoredLineupEntry> {
        dataSource.connection.use { connection ->
            return loadEventLineup(
                connection = connection,
                eventId = eventId,
            )
        }
    }

    /** Находит lineup entry, созданный из конкретной approved-заявки. */
    override fun findApplicationLineupEntry(
        eventId: String,
        applicationId: String,
    ): StoredLineupEntry? {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                """
                SELECT
                    le.id,
                    le.event_id,
                    le.comedian_user_id,
                    comedian.display_name AS comedian_display_name,
                    comedian.username AS comedian_username,
                    le.application_id,
                    le.order_index,
                    le.status,
                    le.notes,
                    le.created_at,
                    le.updated_at
                FROM lineup_entries le
                JOIN users comedian
                  ON comedian.id = le.comedian_user_id
                WHERE le.event_id = ?
                  AND le.application_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.setObject(2, UUID.fromString(applicationId))
                statement.executeQuery().use { result ->
                    if (result.next()) result.toStoredLineupEntry() else null
                }
            }
        }
    }

    /** Создает новую draft-запись lineup в хвосте текущего порядка. */
    override fun createLineupEntry(
        eventId: String,
        comedianUserId: String,
        applicationId: String?,
        status: LineupEntryStatus,
        notes: String?,
    ): StoredLineupEntry {
        val entryId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val nextOrderIndex = connection.prepareStatement(
                    """
                    SELECT COALESCE(MAX(order_index), 0) + 1
                    FROM lineup_entries
                    WHERE event_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(eventId))
                    statement.executeQuery().use { result ->
                        result.next()
                        result.getInt(1)
                    }
                }
                connection.prepareStatement(
                    """
                    INSERT INTO lineup_entries (
                        id,
                        event_id,
                        comedian_user_id,
                        application_id,
                        order_index,
                        status,
                        notes,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(entryId))
                    statement.setObject(2, UUID.fromString(eventId))
                    statement.setObject(3, UUID.fromString(comedianUserId))
                    statement.setObject(4, applicationId?.let(UUID::fromString))
                    statement.setInt(5, nextOrderIndex)
                    statement.setString(6, status.wireName)
                    statement.setString(7, notes)
                    statement.executeUpdate()
                }
                val entry = requireNotNull(loadLineupEntry(connection, eventId, entryId))
                connection.commit()
                return entry
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Переставляет весь lineup события в одном транзакционном блоке. */
    override fun reorderEventLineup(
        eventId: String,
        updates: List<LineupEntryOrderUpdate>,
    ): List<StoredLineupEntry> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val offset = updates.size
                updates.forEach { update ->
                    connection.prepareStatement(
                        """
                        UPDATE lineup_entries
                        SET order_index = ?,
                            updated_at = NOW()
                        WHERE event_id = ?
                          AND id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setInt(1, update.orderIndex + offset)
                        statement.setObject(2, UUID.fromString(eventId))
                        statement.setObject(3, UUID.fromString(update.entryId))
                        statement.executeUpdate()
                    }
                }
                updates.forEach { update ->
                    connection.prepareStatement(
                        """
                        UPDATE lineup_entries
                        SET order_index = ?,
                            updated_at = NOW()
                        WHERE event_id = ?
                          AND id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setInt(1, update.orderIndex)
                        statement.setObject(2, UUID.fromString(eventId))
                        statement.setObject(3, UUID.fromString(update.entryId))
                        statement.executeUpdate()
                    }
                }
                val lineup = loadEventLineup(connection, eventId)
                connection.commit()
                return lineup
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Загружает одну запись lineup по event/id. */
    private fun loadLineupEntry(
        connection: Connection,
        eventId: String,
        entryId: String,
    ): StoredLineupEntry? {
        return connection.prepareStatement(
            """
            SELECT
                le.id,
                le.event_id,
                le.comedian_user_id,
                comedian.display_name AS comedian_display_name,
                comedian.username AS comedian_username,
                le.application_id,
                le.order_index,
                le.status,
                le.notes,
                le.created_at,
                le.updated_at
            FROM lineup_entries le
            JOIN users comedian
              ON comedian.id = le.comedian_user_id
            WHERE le.event_id = ?
              AND le.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, UUID.fromString(entryId))
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredLineupEntry() else null
            }
        }
    }

    /** Загружает весь lineup события в порядке `order_index`. */
    private fun loadEventLineup(
        connection: Connection,
        eventId: String,
    ): List<StoredLineupEntry> {
        return connection.prepareStatement(
            """
            SELECT
                le.id,
                le.event_id,
                le.comedian_user_id,
                comedian.display_name AS comedian_display_name,
                comedian.username AS comedian_username,
                le.application_id,
                le.order_index,
                le.status,
                le.notes,
                le.created_at,
                le.updated_at
            FROM lineup_entries le
            JOIN users comedian
              ON comedian.id = le.comedian_user_id
            WHERE le.event_id = ?
            ORDER BY le.order_index, le.id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.toStoredLineupEntry())
                    }
                }
            }
        }
    }
}

/**
 * Преобразует SQL-строку lineup entry в read-only persistence модель.
 */
private fun ResultSet.toStoredLineupEntry(): StoredLineupEntry {
    return StoredLineupEntry(
        id = getObject("id", UUID::class.java).toString(),
        eventId = getObject("event_id", UUID::class.java).toString(),
        comedianUserId = getObject("comedian_user_id", UUID::class.java).toString(),
        comedianDisplayName = getString("comedian_display_name"),
        comedianUsername = getString("comedian_username"),
        applicationId = getObject("application_id", UUID::class.java)?.toString(),
        orderIndex = getInt("order_index"),
        status = LineupEntryStatus.fromWireName(getString("status"))
            ?: error("Unknown lineup entry status"),
        notes = getString("notes"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
}
