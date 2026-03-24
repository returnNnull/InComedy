package com.bam.incomedy.server.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация persistence для заявок комиков.
 *
 * Репозиторий изолирует schema/read-model детали `comedian applications` от HTTP/service слоя и
 * возвращает уже обогащенные organizer-friendly записи с именами комика и reviewer-а.
 */
class PostgresComedianApplicationRepository(
    private val dataSource: DataSource,
) : ComedianApplicationRepository {
    /** Возвращает упорядоченный organizer список заявок события. */
    override fun listEventApplications(eventId: String): List<StoredComedianApplication> {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                """
                SELECT
                    ca.id,
                    ca.event_id,
                    ca.comedian_user_id,
                    comedian.display_name AS comedian_display_name,
                    comedian.username AS comedian_username,
                    ca.status,
                    ca.note,
                    ca.reviewed_by_user_id,
                    reviewer.display_name AS reviewed_by_display_name,
                    ca.created_at,
                    ca.updated_at,
                    ca.status_updated_at
                FROM comedian_applications ca
                JOIN users comedian
                  ON comedian.id = ca.comedian_user_id
                LEFT JOIN users reviewer
                  ON reviewer.id = ca.reviewed_by_user_id
                WHERE ca.event_id = ?
                ORDER BY ca.created_at, ca.id
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toStoredComedianApplication())
                        }
                    }
                }
            }
        }
    }

    /** Загружает одну заявку по event/application id. */
    override fun findEventApplication(
        eventId: String,
        applicationId: String,
    ): StoredComedianApplication? {
        dataSource.connection.use { connection ->
            return loadEventApplication(
                connection = connection,
                eventId = eventId,
                applicationId = applicationId,
            )
        }
    }

    /** Находит уже существующую заявку конкретного комика на событие. */
    override fun findComedianApplication(
        eventId: String,
        comedianUserId: String,
    ): StoredComedianApplication? {
        dataSource.connection.use { connection ->
            return connection.prepareStatement(
                """
                SELECT
                    ca.id,
                    ca.event_id,
                    ca.comedian_user_id,
                    comedian.display_name AS comedian_display_name,
                    comedian.username AS comedian_username,
                    ca.status,
                    ca.note,
                    ca.reviewed_by_user_id,
                    reviewer.display_name AS reviewed_by_display_name,
                    ca.created_at,
                    ca.updated_at,
                    ca.status_updated_at
                FROM comedian_applications ca
                JOIN users comedian
                  ON comedian.id = ca.comedian_user_id
                LEFT JOIN users reviewer
                  ON reviewer.id = ca.reviewed_by_user_id
                WHERE ca.event_id = ?
                  AND ca.comedian_user_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.setObject(2, UUID.fromString(comedianUserId))
                statement.executeQuery().use { result ->
                    if (result.next()) result.toStoredComedianApplication() else null
                }
            }
        }
    }

    /** Создает новую заявку и сразу возвращает ее обогащенную read-model запись. */
    override fun createComedianApplication(
        eventId: String,
        comedianUserId: String,
        note: String?,
        status: ComedianApplicationStatus,
    ): StoredComedianApplication {
        val applicationId = UUID.randomUUID().toString()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO comedian_applications (
                    id,
                    event_id,
                    comedian_user_id,
                    status,
                    note,
                    created_at,
                    updated_at,
                    status_updated_at
                ) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), NOW())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.fromString(applicationId))
                statement.setObject(2, UUID.fromString(eventId))
                statement.setObject(3, UUID.fromString(comedianUserId))
                statement.setString(4, status.wireName)
                statement.setString(5, note)
                statement.executeUpdate()
            }
            return requireNotNull(loadEventApplication(connection, eventId, applicationId))
        }
    }

    /** Обновляет review-статус и reviewer-а, сохраняя остальное состояние заявки. */
    override fun updateComedianApplicationStatus(
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
        reviewedByUserId: String,
    ): StoredComedianApplication? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE comedian_applications
                SET status = ?,
                    reviewed_by_user_id = ?,
                    updated_at = NOW(),
                    status_updated_at = NOW()
                WHERE event_id = ?
                  AND id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, status.wireName)
                statement.setObject(2, UUID.fromString(reviewedByUserId))
                statement.setObject(3, UUID.fromString(eventId))
                statement.setObject(4, UUID.fromString(applicationId))
                if (statement.executeUpdate() == 0) {
                    return null
                }
            }
            return loadEventApplication(
                connection = connection,
                eventId = eventId,
                applicationId = applicationId,
            )
        }
    }

    /** Загружает одну заявку вместе с joined user/reviewer данными. */
    private fun loadEventApplication(
        connection: Connection,
        eventId: String,
        applicationId: String,
    ): StoredComedianApplication? {
        return connection.prepareStatement(
            """
            SELECT
                ca.id,
                ca.event_id,
                ca.comedian_user_id,
                comedian.display_name AS comedian_display_name,
                comedian.username AS comedian_username,
                ca.status,
                ca.note,
                ca.reviewed_by_user_id,
                reviewer.display_name AS reviewed_by_display_name,
                ca.created_at,
                ca.updated_at,
                ca.status_updated_at
            FROM comedian_applications ca
            JOIN users comedian
              ON comedian.id = ca.comedian_user_id
            LEFT JOIN users reviewer
              ON reviewer.id = ca.reviewed_by_user_id
            WHERE ca.event_id = ?
              AND ca.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, UUID.fromString(applicationId))
            statement.executeQuery().use { result ->
                if (result.next()) result.toStoredComedianApplication() else null
            }
        }
    }
}

/**
 * Преобразует SQL-строку заявки комика в read-only persistence модель.
 */
private fun ResultSet.toStoredComedianApplication(): StoredComedianApplication {
    return StoredComedianApplication(
        id = getObject("id", UUID::class.java).toString(),
        eventId = getObject("event_id", UUID::class.java).toString(),
        comedianUserId = getObject("comedian_user_id", UUID::class.java).toString(),
        comedianDisplayName = getString("comedian_display_name"),
        comedianUsername = getString("comedian_username"),
        status = ComedianApplicationStatus.fromWireName(getString("status"))
            ?: error("Unknown comedian application status"),
        note = getString("note"),
        reviewedByUserId = getObject("reviewed_by_user_id", UUID::class.java)?.toString(),
        reviewedByDisplayName = getString("reviewed_by_display_name"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        statusUpdatedAt = getObject("status_updated_at", OffsetDateTime::class.java),
    )
}
