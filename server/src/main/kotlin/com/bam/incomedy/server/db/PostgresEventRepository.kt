package com.bam.incomedy.server.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация organizer event persistence.
 *
 * Репозиторий изолирует event create/list/detail/lifecycle transitions и хранение event-local
 * override-ов от venue/workspace persistence, чтобы `events` slice не размывался в сторону
 * ticketing.
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
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(enrichEvent(connection, result.toStoredOrganizerEventBase()))
                        }
                    }
                }
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

    /** Возвращает одно событие вместе со snapshot и event-local overrides. */
    override fun findEvent(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return loadEvent(connection, eventId)
        }
    }

    /** Полностью заменяет event-local organizer configuration поверх frozen snapshot. */
    override fun updateEvent(
        eventId: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        currency: String,
        visibility: String,
        priceZones: List<StoredEventPriceZone>,
        pricingAssignments: List<StoredEventPricingAssignment>,
        availabilityOverrides: List<StoredEventAvailabilityOverride>,
    ): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    """
                    UPDATE organizer_events
                    SET
                        title = ?,
                        description = ?,
                        starts_at = ?,
                        doors_open_at = ?,
                        ends_at = ?,
                        currency = ?,
                        visibility = ?,
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, title)
                    statement.setString(2, description)
                    statement.setObject(3, startsAt)
                    statement.setObject(4, doorsOpenAt)
                    statement.setObject(5, endsAt)
                    statement.setString(6, currency)
                    statement.setString(7, visibility)
                    statement.setObject(8, UUID.fromString(eventId))
                    if (statement.executeUpdate() == 0) {
                        connection.rollback()
                        return null
                    }
                }

                deleteOverrideCollections(connection, eventId)
                insertPriceZones(connection, eventId, priceZones)
                insertPricingAssignments(connection, eventId, pricingAssignments)
                insertAvailabilityOverrides(connection, eventId, availabilityOverrides)

                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
            return loadEvent(connection, eventId)
        }
    }

    /** Публикует draft-событие и возвращает его текущее состояние. */
    override fun publishEvent(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return updateLifecycleState(
                connection = connection,
                eventId = eventId,
                status = "published",
            )
        }
    }

    /** Открывает продажи опубликованного события и возвращает текущее состояние. */
    override fun openEventSales(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return updateLifecycleState(
                connection = connection,
                eventId = eventId,
                salesStatus = "open",
            )
        }
    }

    /** Ставит продажи события на паузу и возвращает текущее состояние. */
    override fun pauseEventSales(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return updateLifecycleState(
                connection = connection,
                eventId = eventId,
                salesStatus = "paused",
            )
        }
    }

    /** Отменяет событие, одновременно закрывая продажи. */
    override fun cancelEvent(eventId: String): StoredOrganizerEvent? {
        dataSource.connection.use { connection ->
            return updateLifecycleState(
                connection = connection,
                eventId = eventId,
                status = "canceled",
                salesStatus = "closed",
            )
        }
    }

    /**
     * Применяет точечный lifecycle transition к organizer event без затрагивания snapshot/override
     * коллекций.
     */
    private fun updateLifecycleState(
        connection: Connection,
        eventId: String,
        status: String? = null,
        salesStatus: String? = null,
    ): StoredOrganizerEvent? {
        val assignments = buildList {
            if (status != null) add("status = ?")
            if (salesStatus != null) add("sales_status = ?")
            add("updated_at = NOW()")
        }
        connection.prepareStatement(
            """
            UPDATE organizer_events
            SET ${assignments.joinToString(", ")}
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            var parameterIndex = 1
            if (status != null) {
                statement.setString(parameterIndex++, status)
            }
            if (salesStatus != null) {
                statement.setString(parameterIndex++, salesStatus)
            }
            statement.setObject(parameterIndex, UUID.fromString(eventId))
            if (statement.executeUpdate() == 0) return null
        }
        return loadEvent(connection, eventId)
    }

    /** Загружает одно событие вместе со snapshot и event-local overrides по его id. */
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
                enrichEvent(connection, result.toStoredOrganizerEventBase())
            }
        }
    }

    /** Подгружает event-local override collections к базовому stored event. */
    private fun enrichEvent(
        connection: Connection,
        baseEvent: StoredOrganizerEvent,
    ): StoredOrganizerEvent {
        return baseEvent.copy(
            priceZones = loadPriceZones(connection, baseEvent.id),
            pricingAssignments = loadPricingAssignments(connection, baseEvent.id),
            availabilityOverrides = loadAvailabilityOverrides(connection, baseEvent.id),
        )
    }

    /** Загружает event-local ценовые зоны в сохраненном порядке. */
    private fun loadPriceZones(
        connection: Connection,
        eventId: String,
    ): List<StoredEventPriceZone> {
        return connection.prepareStatement(
            """
            SELECT
                id,
                name,
                price_minor,
                currency,
                sales_start_at,
                sales_end_at,
                source_template_price_zone_id
            FROM event_price_zones
            WHERE event_id = ?
            ORDER BY sort_order, id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredEventPriceZone(
                                id = result.getString("id"),
                                name = result.getString("name"),
                                priceMinor = result.getInt("price_minor"),
                                currency = result.getString("currency"),
                                salesStartAt = result.getObject("sales_start_at", OffsetDateTime::class.java),
                                salesEndAt = result.getObject("sales_end_at", OffsetDateTime::class.java),
                                sourceTemplatePriceZoneId = result.getString("source_template_price_zone_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Загружает event-local pricing assignments. */
    private fun loadPricingAssignments(
        connection: Connection,
        eventId: String,
    ): List<StoredEventPricingAssignment> {
        return connection.prepareStatement(
            """
            SELECT target_type, target_ref, event_price_zone_id
            FROM event_pricing_assignments
            WHERE event_id = ?
            ORDER BY target_type, target_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredEventPricingAssignment(
                                targetType = result.getString("target_type"),
                                targetRef = result.getString("target_ref"),
                                eventPriceZoneId = result.getString("event_price_zone_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Загружает event-local availability overrides. */
    private fun loadAvailabilityOverrides(
        connection: Connection,
        eventId: String,
    ): List<StoredEventAvailabilityOverride> {
        return connection.prepareStatement(
            """
            SELECT target_type, target_ref, availability_status
            FROM event_availability_overrides
            WHERE event_id = ?
            ORDER BY target_type, target_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            StoredEventAvailabilityOverride(
                                targetType = result.getString("target_type"),
                                targetRef = result.getString("target_ref"),
                                availabilityStatus = result.getString("availability_status"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Удаляет все override collections события перед replace-update. */
    private fun deleteOverrideCollections(
        connection: Connection,
        eventId: String,
    ) {
        connection.prepareStatement(
            "DELETE FROM event_pricing_assignments WHERE event_id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM event_availability_overrides WHERE event_id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            "DELETE FROM event_price_zones WHERE event_id = ?",
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeUpdate()
        }
    }

    /** Вставляет актуальный набор event-local ценовых зон. */
    private fun insertPriceZones(
        connection: Connection,
        eventId: String,
        priceZones: List<StoredEventPriceZone>,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO event_price_zones (
                event_id,
                id,
                source_template_price_zone_id,
                name,
                price_minor,
                currency,
                sales_start_at,
                sales_end_at,
                sort_order,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
        ).use { statement ->
            priceZones.forEachIndexed { index, zone ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.setString(2, zone.id)
                statement.setString(3, zone.sourceTemplatePriceZoneId)
                statement.setString(4, zone.name)
                statement.setInt(5, zone.priceMinor)
                statement.setString(6, zone.currency)
                statement.setObject(7, zone.salesStartAt)
                statement.setObject(8, zone.salesEndAt)
                statement.setInt(9, index)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Вставляет актуальный набор pricing assignments. */
    private fun insertPricingAssignments(
        connection: Connection,
        eventId: String,
        assignments: List<StoredEventPricingAssignment>,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO event_pricing_assignments (
                event_id,
                target_type,
                target_ref,
                event_price_zone_id,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
        ).use { statement ->
            assignments.forEach { assignment ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.setString(2, assignment.targetType)
                statement.setString(3, assignment.targetRef)
                statement.setString(4, assignment.eventPriceZoneId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Вставляет актуальный набор availability overrides. */
    private fun insertAvailabilityOverrides(
        connection: Connection,
        eventId: String,
        overrides: List<StoredEventAvailabilityOverride>,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO event_availability_overrides (
                event_id,
                target_type,
                target_ref,
                availability_status,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
        ).use { statement ->
            overrides.forEach { availabilityOverride ->
                statement.setObject(1, UUID.fromString(eventId))
                statement.setString(2, availabilityOverride.targetType)
                statement.setString(3, availabilityOverride.targetRef)
                statement.setString(4, availabilityOverride.availabilityStatus)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Маппит текущую строку result set в базовый stored organizer event без override collections. */
    private fun ResultSet.toStoredOrganizerEventBase(): StoredOrganizerEvent {
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
