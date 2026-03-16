package com.bam.incomedy.server.db

import java.time.OffsetDateTime

/**
 * Blueprint inventory unit до применения в persistence-слой.
 *
 * @property eventId Идентификатор события.
 * @property inventoryRef Стабильная ссылка на sellable unit.
 * @property inventoryType Тип inventory unit.
 * @property snapshotTargetType Тип исходного snapshot target-а.
 * @property snapshotTargetRef Идентификатор исходного snapshot target-а.
 * @property label Человекочитаемая подпись.
 * @property priceZoneId Разрешенная ценовая зона.
 * @property priceZoneName Отображаемое имя ценовой зоны.
 * @property priceMinor Цена в minor units.
 * @property currency Валюта цены.
 * @property baseStatus Базовая доступность без учета активного hold-а.
 */
data class StoredInventoryUnitBlueprint(
    val eventId: String,
    val inventoryRef: String,
    val inventoryType: String,
    val snapshotTargetType: String,
    val snapshotTargetRef: String,
    val label: String,
    val priceZoneId: String? = null,
    val priceZoneName: String? = null,
    val priceMinor: Int? = null,
    val currency: String,
    val baseStatus: String,
)

/**
 * Сохраненный hold на inventory unit.
 *
 * @property id Идентификатор hold-а.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property inventoryRef Стабильная ссылка на inventory unit.
 * @property userId Идентификатор пользователя-владельца hold-а.
 * @property expiresAt Момент автоматического истечения hold-а.
 * @property status Текущее состояние hold-а.
 */
data class StoredSeatHold(
    val id: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val userId: String,
    val expiresAt: OffsetDateTime,
    val status: String,
)

/**
 * Сохраненная inventory unit с опциональным активным hold-ом.
 *
 * @property id Идентификатор inventory unit.
 * @property eventId Идентификатор события-владельца.
 * @property inventoryRef Стабильная ссылка на sellable unit.
 * @property inventoryType Тип inventory unit.
 * @property snapshotTargetType Тип исходного snapshot target-а.
 * @property snapshotTargetRef Идентификатор исходного snapshot target-а.
 * @property label Человекочитаемая подпись inventory unit.
 * @property priceZoneId Разрешенная ценовая зона.
 * @property priceZoneName Имя ценовой зоны.
 * @property priceMinor Цена в minor units.
 * @property currency Валюта цены.
 * @property baseStatus Базовая доступность без активного hold-а.
 * @property status Текущее состояние inventory unit.
 * @property activeHold Активный hold, если он еще привязан к unit.
 */
data class StoredInventoryUnit(
    val id: String,
    val eventId: String,
    val inventoryRef: String,
    val inventoryType: String,
    val snapshotTargetType: String,
    val snapshotTargetRef: String,
    val label: String,
    val priceZoneId: String? = null,
    val priceZoneName: String? = null,
    val priceMinor: Int? = null,
    val currency: String,
    val baseStatus: String,
    val status: String,
    val activeHold: StoredSeatHold? = null,
)

/**
 * Persistence-контракт первого ticketing foundation slice-а.
 */
interface TicketingRepository {
    /** Синхронизирует inventory units события с текущим derived blueprint и возвращает снимок состояния. */
    fun reconcileInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit>

    /** Создает новый hold на конкретную inventory unit. */
    fun createSeatHold(
        eventId: String,
        inventoryRef: String,
        userId: String,
        expiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredSeatHold

    /** Освобождает hold, если он принадлежит текущему пользователю и еще активен. */
    fun releaseSeatHold(
        holdId: String,
        userId: String,
        now: OffsetDateTime,
    ): StoredSeatHold
}

/** Сигнализирует, что inventory unit не найдена в persistence. */
class TicketingInventoryUnitNotFoundPersistenceException(
    val eventId: String,
    val inventoryRef: String,
) : IllegalStateException("Inventory unit was not found")

/** Сигнализирует, что inventory unit нельзя удержать из-за текущего состояния. */
class TicketingInventoryConflictPersistenceException(
    val inventoryRef: String,
    val currentStatus: String,
) : IllegalStateException("Inventory unit is not available")

/** Сигнализирует, что hold не найден в persistence. */
class TicketingSeatHoldNotFoundPersistenceException(
    val holdId: String,
) : IllegalStateException("Seat hold was not found")

/** Сигнализирует, что hold принадлежит другому пользователю. */
class TicketingSeatHoldPermissionDeniedPersistenceException(
    val holdId: String,
    val userId: String,
) : IllegalStateException("Seat hold belongs to another user")

/** Сигнализирует, что hold уже завершен и не может быть повторно освобожден. */
class TicketingSeatHoldInactivePersistenceException(
    val holdId: String,
    val currentStatus: String,
) : IllegalStateException("Seat hold is no longer active")
