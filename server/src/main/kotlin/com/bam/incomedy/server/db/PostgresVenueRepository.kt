package com.bam.incomedy.server.db

import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация venue management persistence.
 *
 * Класс изолирует площадки и hall templates в отдельный repository path, чтобы venue bounded context
 * не раздувал существующий auth/session/workspace persistence класс еще сильнее.
 */
class PostgresVenueRepository(
    private val dataSource: DataSource,
) : VenueRepository {
    /** Возвращает площадки пользователя по активным workspace membership и подгружает шаблоны. */
    override fun listVenues(userId: String): List<StoredVenue> {
        dataSource.connection.use { connection ->
            val sql = """
                SELECT
                    v.id,
                    v.workspace_id,
                    v.name,
                    v.city,
                    v.address,
                    v.timezone,
                    v.capacity,
                    v.description,
                    v.contacts_json
                FROM organizer_venues v
                JOIN workspace_members wm
                  ON wm.workspace_id = v.workspace_id
                WHERE wm.user_id = ?
                  AND wm.joined_at IS NOT NULL
                ORDER BY LOWER(v.name), v.created_at
            """.trimIndent()
            val venues = connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                StoredVenue(
                                    id = result.getObject("id").toString(),
                                    workspaceId = result.getObject("workspace_id").toString(),
                                    name = result.getString("name"),
                                    city = result.getString("city"),
                                    address = result.getString("address"),
                                    timezone = result.getString("timezone"),
                                    capacity = result.getInt("capacity"),
                                    description = result.getString("description"),
                                    contactsJson = result.getString("contacts_json") ?: "[]",
                                ),
                            )
                        }
                    }
                }
            }
            return venues.map { venue ->
                venue.copy(hallTemplates = loadHallTemplates(connection, venue.id))
            }
        }
    }

    /** Создает площадку и возвращает ее полную сохраненную модель. */
    override fun createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsJson: String,
    ): StoredVenue {
        val venueId = UUID.randomUUID().toString()
        val sql = """
            INSERT INTO organizer_venues (
                id,
                workspace_id,
                name,
                city,
                address,
                timezone,
                capacity,
                description,
                contacts_json,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        """.trimIndent()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(venueId))
                statement.setObject(2, UUID.fromString(workspaceId))
                statement.setString(3, name)
                statement.setString(4, city)
                statement.setString(5, address)
                statement.setString(6, timezone)
                statement.setInt(7, capacity)
                statement.setString(8, description)
                statement.setString(9, contactsJson)
                statement.executeUpdate()
            }
        }
        return requireNotNull(findVenue(venueId))
    }

    /** Возвращает площадку по id вместе с шаблонами. */
    override fun findVenue(venueId: String): StoredVenue? {
        dataSource.connection.use { connection ->
            return loadVenue(connection, venueId)
        }
    }

    /** Создает новый hall template версии 1. */
    override fun createHallTemplate(
        venueId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate {
        val templateId = UUID.randomUUID().toString()
        val sql = """
            INSERT INTO hall_templates (
                id,
                venue_id,
                name,
                version,
                status,
                layout_json,
                cloned_from_template_id,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, 1, ?, ?, NULL, NOW(), NOW())
        """.trimIndent()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(templateId))
                statement.setObject(2, UUID.fromString(venueId))
                statement.setString(3, name)
                statement.setString(4, status)
                statement.setString(5, layoutJson)
                statement.executeUpdate()
            }
        }
        return requireNotNull(findHallTemplate(templateId))
    }

    /** Возвращает hall template по id. */
    override fun findHallTemplate(templateId: String): StoredHallTemplate? {
        dataSource.connection.use { connection ->
            return loadHallTemplate(connection, templateId)
        }
    }

    /** Обновляет hall template и увеличивает версию на единицу. */
    override fun updateHallTemplate(
        templateId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate? {
        val sql = """
            UPDATE hall_templates
            SET
                name = ?,
                status = ?,
                layout_json = ?,
                version = version + 1,
                updated_at = NOW()
            WHERE id = ?
        """.trimIndent()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, name)
                statement.setString(2, status)
                statement.setString(3, layoutJson)
                statement.setObject(4, UUID.fromString(templateId))
                if (statement.executeUpdate() == 0) return null
            }
            return loadHallTemplate(connection, templateId)
        }
    }

    /** Клонирует hall template в ту же площадку с новой сущностью и версией 1. */
    override fun cloneHallTemplate(
        sourceTemplateId: String,
        name: String,
        status: String,
    ): StoredHallTemplate? {
        dataSource.connection.use { connection ->
            val source = loadHallTemplate(connection, sourceTemplateId) ?: return null
            val clonedId = UUID.randomUUID().toString()
            val sql = """
                INSERT INTO hall_templates (
                    id,
                    venue_id,
                    name,
                    version,
                    status,
                    layout_json,
                    cloned_from_template_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 1, ?, ?, ?, NOW(), NOW())
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(clonedId))
                statement.setObject(2, UUID.fromString(source.venueId))
                statement.setString(3, name)
                statement.setString(4, status)
                statement.setString(5, source.layoutJson)
                statement.setObject(6, UUID.fromString(sourceTemplateId))
                statement.executeUpdate()
            }
            return loadHallTemplate(connection, clonedId)
        }
    }

    /** Загружает площадку вместе с вложенными шаблонами. */
    private fun loadVenue(connection: Connection, venueId: String): StoredVenue? {
        val sql = """
            SELECT
                id,
                workspace_id,
                name,
                city,
                address,
                timezone,
                capacity,
                description,
                contacts_json
            FROM organizer_venues
            WHERE id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, UUID.fromString(venueId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredVenue(
                    id = result.getObject("id").toString(),
                    workspaceId = result.getObject("workspace_id").toString(),
                    name = result.getString("name"),
                    city = result.getString("city"),
                    address = result.getString("address"),
                    timezone = result.getString("timezone"),
                    capacity = result.getInt("capacity"),
                    description = result.getString("description"),
                    contactsJson = result.getString("contacts_json") ?: "[]",
                    hallTemplates = loadHallTemplates(connection, venueId),
                )
            }
        }
    }

    /** Загружает все hall templates одной площадки в стабильном порядке. */
    private fun loadHallTemplates(connection: Connection, venueId: String): List<StoredHallTemplate> {
        val sql = """
            SELECT
                id,
                venue_id,
                name,
                version,
                status,
                layout_json
            FROM hall_templates
            WHERE venue_id = ?
            ORDER BY LOWER(name), version DESC, created_at
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, UUID.fromString(venueId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredHallTemplate(
                                id = result.getObject("id").toString(),
                                venueId = result.getObject("venue_id").toString(),
                                name = result.getString("name"),
                                version = result.getInt("version"),
                                status = result.getString("status"),
                                layoutJson = result.getString("layout_json"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Загружает один hall template по его id. */
    private fun loadHallTemplate(connection: Connection, templateId: String): StoredHallTemplate? {
        val sql = """
            SELECT
                id,
                venue_id,
                name,
                version,
                status,
                layout_json
            FROM hall_templates
            WHERE id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setObject(1, UUID.fromString(templateId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                StoredHallTemplate(
                    id = result.getObject("id").toString(),
                    venueId = result.getObject("venue_id").toString(),
                    name = result.getString("name"),
                    version = result.getInt("version"),
                    status = result.getString("status"),
                    layoutJson = result.getString("layout_json"),
                )
            }
        }
    }
}
