package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.StoredInventoryUnit
import com.bam.incomedy.server.db.StoredInventoryUnitBlueprint
import com.bam.incomedy.server.db.StoredSeatHold
import com.bam.incomedy.server.db.TicketingInventoryConflictPersistenceException
import com.bam.incomedy.server.db.TicketingInventoryUnitNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingRepository
import com.bam.incomedy.server.db.TicketingSeatHoldInactivePersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldNotFoundPersistenceException
import com.bam.incomedy.server.db.TicketingSeatHoldPermissionDeniedPersistenceException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация `TicketingRepository` для route/service тестов ticketing foundation.
 *
 * Репозиторий хранит derived inventory и hold-ы без реальной БД, чтобы детерминированно
 * проверять expiry, release и conflict semantics поверх одного процесса.
 */
class InMemoryTicketingRepository : TicketingRepository {
    /** Mutable inventory units по event и inventory ref. */
    private val inventoryByEventId = linkedMapOf<String, LinkedHashMap<String, MutableInventoryRecord>>()

    /** Последняя organizer revision, с которой уже синхронизирован inventory конкретного события. */
    private val syncedRevisionByEventId = linkedMapOf<String, OffsetDateTime>()

    /** Hold-ы по их id. */
    private val holdsById = linkedMapOf<String, MutableSeatHoldRecord>()

    /** Счетчик inventory sync invocation-ов для route/service regression тестов. */
    var synchronizeInventoryCallCount: Int = 0
        private set

    override fun isInventorySynchronized(
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ): Boolean {
        return syncedRevisionByEventId[eventId] == sourceEventUpdatedAt
    }

    override fun synchronizeInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        sourceEventUpdatedAt: OffsetDateTime,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        synchronizeInventoryCallCount += 1
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        val eventInventory = inventoryByEventId.getOrPut(eventId) { linkedMapOf() }
        inventory.forEach { blueprint ->
            val existing = eventInventory[blueprint.inventoryRef]
            if (existing == null) {
                eventInventory[blueprint.inventoryRef] = MutableInventoryRecord(
                    id = UUID.randomUUID().toString(),
                    eventId = blueprint.eventId,
                    inventoryRef = blueprint.inventoryRef,
                    inventoryType = blueprint.inventoryType,
                    snapshotTargetType = blueprint.snapshotTargetType,
                    snapshotTargetRef = blueprint.snapshotTargetRef,
                    label = blueprint.label,
                    priceZoneId = blueprint.priceZoneId,
                    priceZoneName = blueprint.priceZoneName,
                    priceMinor = blueprint.priceMinor,
                    currency = blueprint.currency,
                    baseStatus = blueprint.baseStatus,
                    status = blueprint.baseStatus,
                )
            } else {
                existing.inventoryType = blueprint.inventoryType
                existing.snapshotTargetType = blueprint.snapshotTargetType
                existing.snapshotTargetRef = blueprint.snapshotTargetRef
                existing.label = blueprint.label
                existing.priceZoneId = blueprint.priceZoneId
                existing.priceZoneName = blueprint.priceZoneName
                existing.priceMinor = blueprint.priceMinor
                existing.currency = blueprint.currency
                existing.baseStatus = blueprint.baseStatus
                existing.status = when {
                    existing.activeHoldId != null -> "held"
                    existing.status == "sold" -> "sold"
                    else -> blueprint.baseStatus
                }
            }
        }
        syncedRevisionByEventId[eventId] = sourceEventUpdatedAt
        return eventInventory.toStoredInventory()
    }

    override fun listInventory(
        eventId: String,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit> {
        expireOverdueHolds(
            eventId = eventId,
            now = now,
        )
        return inventoryByEventId[eventId].orEmpty().toStoredInventory()
    }

    override fun createSeatHold(
        eventId: String,
        inventoryRef: String,
        userId: String,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredSeatHold {
        val eventInventory = inventoryByEventId[eventId]
        val inventoryRecord = eventInventory?.get(inventoryRef)
            ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                eventId = eventId,
                inventoryRef = inventoryRef,
            )
        inventoryRecord.activeHoldId?.let { holdId ->
            val hold = holdsById[holdId]
            if (hold != null && hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                expireHold(
                    inventoryRecord = inventoryRecord,
                    hold = hold,
                )
            }
        }
        if (inventoryRecord.status != "available") {
            throw TicketingInventoryConflictPersistenceException(
                inventoryRef = inventoryRef,
                currentStatus = inventoryRecord.status,
            )
        }
        val hold = MutableSeatHoldRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            inventoryUnitId = inventoryRecord.id,
            inventoryRef = inventoryRef,
            userId = userId,
            expiresAt = expiresAt,
            status = "active",
        )
        holdsById[hold.id] = hold
        inventoryRecord.activeHoldId = hold.id
        inventoryRecord.status = "held"
        return hold.toStored()
    }

    override fun releaseSeatHold(
        holdId: String,
        userId: String,
        now: OffsetDateTime,
    ): StoredSeatHold {
        val hold = holdsById[holdId]
            ?: throw TicketingSeatHoldNotFoundPersistenceException(holdId)
        if (hold.userId != userId) {
            throw TicketingSeatHoldPermissionDeniedPersistenceException(
                holdId = holdId,
                userId = userId,
            )
        }
        val inventoryRecord = inventoryByEventId[hold.eventId]
            ?.values
            ?.firstOrNull { it.id == hold.inventoryUnitId }
            ?: throw TicketingInventoryUnitNotFoundPersistenceException(
                eventId = hold.eventId,
                inventoryRef = hold.inventoryRef,
            )
        if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
            expireHold(
                inventoryRecord = inventoryRecord,
                hold = hold,
            )
            throw TicketingSeatHoldInactivePersistenceException(
                holdId = holdId,
                currentStatus = "expired",
            )
        }
        if (hold.status != "active") {
            throw TicketingSeatHoldInactivePersistenceException(
                holdId = holdId,
                currentStatus = hold.status,
            )
        }
        hold.status = "released"
        inventoryRecord.activeHoldId = null
        inventoryRecord.status = inventoryRecord.baseStatus
        return hold.toStored()
    }

    /** Истекает все просроченные hold-ы конкретного события. */
    private fun expireOverdueHolds(
        eventId: String,
        now: OffsetDateTime,
    ) {
        val eventInventory = inventoryByEventId[eventId].orEmpty().values
        eventInventory.forEach { inventoryRecord ->
            val holdId = inventoryRecord.activeHoldId ?: return@forEach
            val hold = holdsById[holdId] ?: return@forEach
            if (hold.status == "active" && !hold.expiresAt.isAfter(now)) {
                expireHold(
                    inventoryRecord = inventoryRecord,
                    hold = hold,
                )
            }
        }
    }

    /** Переводит hold в expired и возвращает inventory unit к base status. */
    private fun expireHold(
        inventoryRecord: MutableInventoryRecord,
        hold: MutableSeatHoldRecord,
    ) {
        hold.status = "expired"
        inventoryRecord.activeHoldId = null
        inventoryRecord.status = inventoryRecord.baseStatus
    }

    /** Собирает read-only inventory snapshot из mutable event-local state. */
    private fun Map<String, MutableInventoryRecord>.toStoredInventory(): List<StoredInventoryUnit> {
        return values
            .sortedBy(MutableInventoryRecord::inventoryRef)
            .map { record -> record.toStored(holdsById) }
    }

    /** Mutable inventory unit record. */
    private data class MutableInventoryRecord(
        val id: String,
        val eventId: String,
        val inventoryRef: String,
        var inventoryType: String,
        var snapshotTargetType: String,
        var snapshotTargetRef: String,
        var label: String,
        var priceZoneId: String?,
        var priceZoneName: String?,
        var priceMinor: Int?,
        var currency: String,
        var baseStatus: String,
        var status: String,
        var activeHoldId: String? = null,
    ) {
        /** Преобразует mutable inventory unit в stored model. */
        fun toStored(
            holdsById: Map<String, MutableSeatHoldRecord>,
        ): StoredInventoryUnit {
            return StoredInventoryUnit(
                id = id,
                eventId = eventId,
                inventoryRef = inventoryRef,
                inventoryType = inventoryType,
                snapshotTargetType = snapshotTargetType,
                snapshotTargetRef = snapshotTargetRef,
                label = label,
                priceZoneId = priceZoneId,
                priceZoneName = priceZoneName,
                priceMinor = priceMinor,
                currency = currency,
                baseStatus = baseStatus,
                status = status,
                activeHold = activeHoldId
                    ?.let(holdsById::get)
                    ?.toStored(),
            )
        }
    }

    /** Mutable hold record. */
    private data class MutableSeatHoldRecord(
        val id: String,
        val eventId: String,
        val inventoryUnitId: String,
        val inventoryRef: String,
        val userId: String,
        val expiresAt: OffsetDateTime,
        var status: String,
    ) {
        /** Преобразует mutable hold в stored model. */
        fun toStored(): StoredSeatHold {
            return StoredSeatHold(
                id = id,
                eventId = eventId,
                inventoryUnitId = inventoryUnitId,
                inventoryRef = inventoryRef,
                userId = userId,
                expiresAt = expiresAt,
                status = status,
            )
        }
    }
}
