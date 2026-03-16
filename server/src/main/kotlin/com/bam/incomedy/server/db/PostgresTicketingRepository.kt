package com.bam.incomedy.server.db

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL-реализация ticketing foundation persistence.
 *
 * Репозиторий хранит derived inventory units и hold-состояние отдельно от organizer event
 * snapshot-а, чтобы ticketing transitions не мутировали frozen layout и event-local overrides.
 */
class PostgresTicketingRepository(
    private val dataSource: DataSource,
) : TicketingRepository {
    /** Проверяет, совпадает ли persisted inventory sync marker с текущей organizer revision. */
    override fun isInventorySynchronized(
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ): Boolean {
        dataSource.connection.use { connection ->
            return loadInventorySyncState(
                connection = connection,
                eventId = eventId,
            ) == sourceEventUpdatedAt
        }
    }

    /** Синхронизирует derived inventory только на write path или при stale inventory revision. */
    override fun synchronizeInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        sourceEventUpdatedAt: OffsetDateTime,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                inventory.forEach { blueprint ->
                    upsertInventoryUnit(
                        connection = connection,
                        blueprint = blueprint,
                    )
                }
                upsertInventorySyncState(
                    connection = connection,
                    eventId = eventId,
                    sourceEventUpdatedAt = sourceEventUpdatedAt,
                )
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
            return loadInventory(
                connection = connection,
                eventId = eventId,
            )
        }
    }

    /** Возвращает inventory snapshot события без полного derived upsert-а на каждый read. */
    override fun listInventory(
        eventId: String,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                expireOverdueHolds(
                    connection = connection,
                    eventId = eventId,
                    now = now,
                )
                val inventory = loadInventory(
                    connection = connection,
                    eventId = eventId,
                )
                connection.commit()
                return inventory
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Создает новый hold, если unit существует и сейчас доступна для резерва. */
    override fun createSeatHold(
        eventId: String,
        inventoryRef: String,
        userId: String,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredSeatHold {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val lockedUnit = loadInventoryUnit(
                    connection = connection,
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                    lockForUpdate = true,
                ) ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                )
                lockedUnit.activeHold?.let { hold ->
                    if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                        expireHold(
                            connection = connection,
                            inventoryUnitId = lockedUnit.id,
                            holdId = hold.id,
                        )
                    }
                }
                val refreshedUnit = loadInventoryUnit(
                    connection = connection,
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                    lockForUpdate = true,
                ) ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                    eventId = eventId,
                    inventoryRef = inventoryRef,
                )
                if (refreshedUnit.status != "available") {
                    throw TicketingInventoryConflictPersistenceException(
                        inventoryRef = inventoryRef,
                        currentStatus = refreshedUnit.status,
                    )
                }

                val holdId = UUID.randomUUID().toString()
                connection.prepareStatement(
                    """
                    INSERT INTO seat_holds (
                        id,
                        event_id,
                        inventory_unit_id,
                        user_id,
                        expires_at,
                        status,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'active', NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.setObject(2, UUID.fromString(eventId))
                    statement.setObject(3, UUID.fromString(refreshedUnit.id))
                    statement.setObject(4, UUID.fromString(userId))
                    statement.setObject(5, expiresAt)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        active_hold_id = ?,
                        status = 'held',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.setObject(2, UUID.fromString(refreshedUnit.id))
                    statement.executeUpdate()
                }
                val hold = loadSeatHold(
                    connection = connection,
                    holdId = holdId,
                ) ?: error("Persisted hold must be readable right after insert")
                connection.commit()
                return hold
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Освобождает hold и восстанавливает inventory unit к ее базовой доступности.
     *
     * Лок берется сначала на inventory row, чтобы create/release/expiry flows всегда шли в одном
     * порядке и не образовывали deadlock через пересечение `inventory -> hold` и `hold -> inventory`.
     */
    override fun releaseSeatHold(
        holdId: String,
        userId: String,
        now: OffsetDateTime,
    ): StoredSeatHold {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            var committed = false
            try {
                val lockedHold = loadSeatHoldLockedByInventory(
                    connection = connection,
                    holdId = holdId,
                ) ?: throw TicketingSeatHoldNotFoundPersistenceException(holdId)
                if (lockedHold.userId != userId) {
                    throw TicketingSeatHoldPermissionDeniedPersistenceException(
                        holdId = holdId,
                        userId = userId,
                    )
                }
                if (lockedHold.status == "active" && !lockedHold.expiresAt.isAfter(now)) {
                    expireHold(
                        connection = connection,
                        inventoryUnitId = lockedHold.inventoryUnitId,
                        holdId = lockedHold.id,
                    )
                    connection.commit()
                    committed = true
                    throw TicketingSeatHoldInactivePersistenceException(
                        holdId = holdId,
                        currentStatus = "expired",
                    )
                }
                if (lockedHold.status != "active") {
                    throw TicketingSeatHoldInactivePersistenceException(
                        holdId = holdId,
                        currentStatus = lockedHold.status,
                    )
                }
                connection.prepareStatement(
                    """
                    UPDATE seat_holds
                    SET
                        status = 'released',
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(holdId))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE ticket_inventory_units
                    SET
                        active_hold_id = NULL,
                        status = base_status,
                        updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(lockedHold.inventoryUnitId))
                    statement.executeUpdate()
                }
                val releasedHold = loadSeatHold(
                    connection = connection,
                    holdId = holdId,
                ) ?: error("Released hold must remain readable")
                connection.commit()
                committed = true
                return releasedHold
            } catch (error: Throwable) {
                if (!committed) {
                    connection.rollback()
                }
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /** Истекает просроченные hold-ы события и восстанавливает их inventory units. */
    private fun expireOverdueHolds(
        connection: Connection,
        eventId: String,
        now: OffsetDateTime,
    ) {
        val expiredPairs = connection.prepareStatement(
            """
            SELECT
                i.id AS inventory_unit_id,
                h.id AS hold_id
            FROM ticket_inventory_units i
            JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
              AND h.status = 'active'
              AND h.expires_at <= ?
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, now)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            result.getObject("inventory_unit_id").toString() to
                                result.getObject("hold_id").toString(),
                        )
                    }
                }
            }
        }
        expiredPairs.forEach { (inventoryUnitId, holdId) ->
            expireHold(
                connection = connection,
                inventoryUnitId = inventoryUnitId,
                holdId = holdId,
            )
        }
    }

    /** Считывает persisted sync marker derived inventory конкретного события. */
    private fun loadInventorySyncState(
        connection: Connection,
        eventId: String,
    ): OffsetDateTime? {
        return connection.prepareStatement(
            """
            SELECT source_event_updated_at
            FROM ticket_inventory_sync_state
            WHERE event_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.getObject("source_event_updated_at", OffsetDateTime::class.java)
            }
        }
    }

    /** Обновляет или создает persisted sync marker для derived inventory события. */
    private fun upsertInventorySyncState(
        connection: Connection,
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ) {
        val updatedRows = connection.prepareStatement(
            """
            UPDATE ticket_inventory_sync_state
            SET
                source_event_updated_at = ?,
                reconciled_at = NOW()
            WHERE event_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, sourceEventUpdatedAt)
            statement.setObject(2, UUID.fromString(eventId))
            statement.executeUpdate()
        }
        if (updatedRows > 0) {
            return
        }
        connection.prepareStatement(
            """
            INSERT INTO ticket_inventory_sync_state (
                event_id,
                source_event_updated_at,
                reconciled_at
            ) VALUES (?, ?, NOW())
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setObject(2, sourceEventUpdatedAt)
            statement.executeUpdate()
        }
    }

    /** Вставляет новую inventory unit или обновляет derived поля уже существующей записи. */
    private fun upsertInventoryUnit(
        connection: Connection,
        blueprint: StoredInventoryUnitBlueprint,
    ) {
        val existing = loadInventoryUnit(
            connection = connection,
            eventId = blueprint.eventId,
            inventoryRef = blueprint.inventoryRef,
            lockForUpdate = true,
        )
        if (existing == null) {
            connection.prepareStatement(
                """
                INSERT INTO ticket_inventory_units (
                    id,
                    event_id,
                    inventory_ref,
                    inventory_type,
                    snapshot_target_type,
                    snapshot_target_ref,
                    label,
                    price_zone_id,
                    price_zone_name,
                    price_minor,
                    currency,
                    base_status,
                    status,
                    active_hold_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NOW(), NOW())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, UUID.fromString(blueprint.eventId))
                statement.setString(3, blueprint.inventoryRef)
                statement.setString(4, blueprint.inventoryType)
                statement.setString(5, blueprint.snapshotTargetType)
                statement.setString(6, blueprint.snapshotTargetRef)
                statement.setString(7, blueprint.label)
                statement.setString(8, blueprint.priceZoneId)
                statement.setString(9, blueprint.priceZoneName)
                statement.setObject(10, blueprint.priceMinor)
                statement.setString(11, blueprint.currency)
                statement.setString(12, blueprint.baseStatus)
                statement.setString(13, blueprint.baseStatus)
                statement.executeUpdate()
            }
            return
        }

        val nextStatus = when {
            existing.activeHold != null -> "held"
            existing.status == "sold" -> "sold"
            else -> blueprint.baseStatus
        }
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                inventory_type = ?,
                snapshot_target_type = ?,
                snapshot_target_ref = ?,
                label = ?,
                price_zone_id = ?,
                price_zone_name = ?,
                price_minor = ?,
                currency = ?,
                base_status = ?,
                status = ?,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, blueprint.inventoryType)
            statement.setString(2, blueprint.snapshotTargetType)
            statement.setString(3, blueprint.snapshotTargetRef)
            statement.setString(4, blueprint.label)
            statement.setString(5, blueprint.priceZoneId)
            statement.setString(6, blueprint.priceZoneName)
            statement.setObject(7, blueprint.priceMinor)
            statement.setString(8, blueprint.currency)
            statement.setString(9, blueprint.baseStatus)
            statement.setString(10, nextStatus)
            statement.setObject(11, UUID.fromString(existing.id))
            statement.executeUpdate()
        }
    }

    /** Истекает hold и возвращает inventory unit к ее базовой доступности. */
    private fun expireHold(
        connection: Connection,
        inventoryUnitId: String,
        holdId: String,
    ) {
        connection.prepareStatement(
            """
            UPDATE seat_holds
            SET
                status = 'expired',
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """
            UPDATE ticket_inventory_units
            SET
                active_hold_id = NULL,
                status = base_status,
                updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(inventoryUnitId))
            statement.executeUpdate()
        }
    }

    /** Загружает весь текущий inventory snapshot события вместе с привязанными активными hold-ами. */
    private fun loadInventory(
        connection: Connection,
        eventId: String,
    ): List<StoredInventoryUnit> {
        return connection.prepareStatement(
            """
            SELECT
                i.id,
                i.event_id,
                i.inventory_ref,
                i.inventory_type,
                i.snapshot_target_type,
                i.snapshot_target_ref,
                i.label,
                i.price_zone_id,
                i.price_zone_name,
                i.price_minor,
                i.currency,
                i.base_status,
                i.status,
                h.id AS hold_id,
                h.user_id AS hold_user_id,
                h.expires_at AS hold_expires_at,
                h.status AS hold_status
            FROM ticket_inventory_units i
            LEFT JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
            ORDER BY i.inventory_ref
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(result.toStoredInventoryUnit())
                    }
                }
            }
        }
    }

    /** Загружает одну inventory unit, при необходимости блокируя ее для последующей мутации. */
    private fun loadInventoryUnit(
        connection: Connection,
        eventId: String,
        inventoryRef: String,
        lockForUpdate: Boolean,
    ): StoredInventoryUnit? {
        val lockClause = if (lockForUpdate) " FOR UPDATE OF i" else ""
        return connection.prepareStatement(
            """
            SELECT
                i.id,
                i.event_id,
                i.inventory_ref,
                i.inventory_type,
                i.snapshot_target_type,
                i.snapshot_target_ref,
                i.label,
                i.price_zone_id,
                i.price_zone_name,
                i.price_minor,
                i.currency,
                i.base_status,
                i.status,
                h.id AS hold_id,
                h.user_id AS hold_user_id,
                h.expires_at AS hold_expires_at,
                h.status AS hold_status
            FROM ticket_inventory_units i
            LEFT JOIN seat_holds h
              ON h.id = i.active_hold_id
            WHERE i.event_id = ?
              AND i.inventory_ref = ?$lockClause
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(eventId))
            statement.setString(2, inventoryRef)
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredInventoryUnit()
            }
        }
    }

    /** Загружает hold по его id. */
    private fun loadSeatHold(
        connection: Connection,
        holdId: String,
    ): StoredSeatHold? {
        return connection.prepareStatement(
            """
            SELECT
                h.id,
                h.event_id,
                h.inventory_unit_id,
                i.inventory_ref,
                h.user_id,
                h.expires_at,
                h.status
            FROM seat_holds h
            JOIN ticket_inventory_units i
              ON i.id = h.inventory_unit_id
            WHERE h.id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                result.toStoredSeatHold()
            }
        }
    }

    /** Загружает hold, сериализуя его через inventory row lock. */
    private fun loadSeatHoldLockedByInventory(
        connection: Connection,
        holdId: String,
    ): LockedSeatHoldRow? {
        return connection.prepareStatement(
            """
            SELECT
                h.id,
                h.event_id,
                h.inventory_unit_id,
                i.inventory_ref,
                h.user_id,
                h.expires_at,
                h.status
            FROM seat_holds h
            JOIN ticket_inventory_units i
              ON i.id = h.inventory_unit_id
            WHERE h.id = ?
            FOR UPDATE OF i
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(holdId))
            statement.executeQuery().use { result ->
                if (!result.next()) return@use null
                LockedSeatHoldRow(
                    id = result.getObject("id").toString(),
                    eventId = result.getObject("event_id").toString(),
                    inventoryUnitId = result.getObject("inventory_unit_id").toString(),
                    inventoryRef = result.getString("inventory_ref"),
                    userId = result.getObject("user_id").toString(),
                    expiresAt = result.getObject("expires_at", OffsetDateTime::class.java),
                    status = result.getString("status"),
                )
            }
        }
    }

    /** Маппит текущую строку query inventory в stored model. */
    private fun java.sql.ResultSet.toStoredInventoryUnit(): StoredInventoryUnit {
        val holdId = getString("hold_id")
        return StoredInventoryUnit(
            id = getObject("id").toString(),
            eventId = getObject("event_id").toString(),
            inventoryRef = getString("inventory_ref"),
            inventoryType = getString("inventory_type"),
            snapshotTargetType = getString("snapshot_target_type"),
            snapshotTargetRef = getString("snapshot_target_ref"),
            label = getString("label"),
            priceZoneId = getString("price_zone_id"),
            priceZoneName = getString("price_zone_name"),
            priceMinor = getObject("price_minor") as Int?,
            currency = getString("currency"),
            baseStatus = getString("base_status"),
            status = getString("status"),
            activeHold = holdId?.let {
                StoredSeatHold(
                    id = it,
                    eventId = getObject("event_id").toString(),
                    inventoryUnitId = getObject("id").toString(),
                    inventoryRef = getString("inventory_ref"),
                    userId = getObject("hold_user_id").toString(),
                    expiresAt = getObject("hold_expires_at", OffsetDateTime::class.java),
                    status = getString("hold_status"),
                )
            },
        )
    }

    /** Маппит текущую строку query hold в stored hold model. */
    private fun java.sql.ResultSet.toStoredSeatHold(): StoredSeatHold {
        return StoredSeatHold(
            id = getObject("id").toString(),
            eventId = getObject("event_id").toString(),
            inventoryUnitId = getObject("inventory_unit_id").toString(),
            inventoryRef = getString("inventory_ref"),
            userId = getObject("user_id").toString(),
            expiresAt = getObject("expires_at", OffsetDateTime::class.java),
            status = getString("status"),
        )
    }
}

/**
 * Lock-carrier для release flow, которому нужен и hold, и базовый статус inventory unit.
 *
 * @property id Идентификатор hold-а.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property inventoryRef Стабильная ссылка inventory unit.
 * @property userId Идентификатор владельца hold-а.
 * @property expiresAt Момент истечения hold-а.
 * @property status Текущий статус hold-а.
 */
private data class LockedSeatHoldRow(
    val id: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val userId: String,
    val expiresAt: OffsetDateTime,
    val status: String,
)
