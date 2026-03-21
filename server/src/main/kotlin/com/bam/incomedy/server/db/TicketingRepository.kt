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
 * Зафиксированная позиция ticket order-а.
 *
 * @property orderId Идентификатор заказа-владельца.
 * @property inventoryUnitId Идентификатор inventory unit.
 * @property inventoryRef Стабильная ссылка inventory unit.
 * @property label Человекочитаемая подпись позиции.
 * @property priceMinor Цена позиции в minor units.
 * @property currency Валюта позиции.
 */
data class StoredTicketOrderLine(
    val orderId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val label: String,
    val priceMinor: Int,
    val currency: String,
)

/**
 * Сохраненный checkout order, собранный из активных hold-ов пользователя.
 *
 * @property id Идентификатор заказа.
 * @property eventId Идентификатор события.
 * @property userId Идентификатор пользователя-владельца.
 * @property status Текущий статус checkout order-а.
 * @property currency Валюта заказа.
 * @property totalMinor Итоговая сумма в minor units.
 * @property checkoutExpiresAt Момент автоматического истечения checkout lock-а.
 * @property lines Зафиксированные позиции заказа.
 */
data class StoredTicketOrder(
    val id: String,
    val eventId: String,
    val userId: String,
    val status: String,
    val currency: String,
    val totalMinor: Int,
    val checkoutExpiresAt: OffsetDateTime,
    val lines: List<StoredTicketOrderLine>,
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
    /**
     * Проверяет, соответствует ли сохраненный inventory snapshot текущей organizer revision события.
     *
     * Реализация должна опираться на устойчивый persisted sync marker, чтобы `GET inventory` не
     * превращался в полный write-heavy reconcile на каждый запрос.
     */
    fun isInventorySynchronized(
        eventId: String,
        sourceEventUpdatedAt: OffsetDateTime,
    ): Boolean

    /** Синхронизирует inventory units события с текущим derived blueprint и фиксирует revision sync marker. */
    fun synchronizeInventory(
        eventId: String,
        inventory: List<StoredInventoryUnitBlueprint>,
        sourceEventUpdatedAt: OffsetDateTime,
        now: OffsetDateTime,
    ): List<StoredInventoryUnit>

    /** Возвращает текущий inventory snapshot события, одновременно expiring просроченные hold-ы. */
    fun listInventory(
        eventId: String,
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

    /** Создает checkout order и переводит связанные inventory unit-ы в pending payment. */
    fun createTicketOrder(
        eventId: String,
        holdIds: List<String>,
        userId: String,
        checkoutExpiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredTicketOrder

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

/** Сигнализирует, что checkout order содержит hold чужого пользователя. */
class TicketingCheckoutHoldPermissionDeniedPersistenceException(
    val holdId: String,
    val userId: String,
) : IllegalStateException("Checkout hold belongs to another user")

/** Сигнализирует, что checkout order содержит hold другого события. */
class TicketingCheckoutHoldEventMismatchPersistenceException(
    val holdId: String,
    val holdEventId: String,
    val requestedEventId: String,
) : IllegalStateException("Checkout hold belongs to another event")

/** Сигнализирует, что checkout order нельзя собрать из-за состояния hold-а или inventory unit. */
class TicketingCheckoutConflictPersistenceException(
    val holdId: String,
    val reasonCode: String,
) : IllegalStateException("Checkout order cannot be created from the requested hold")

/** Сигнализирует, что выбранная inventory unit не имеет фиксированной цены для checkout-а. */
class TicketingCheckoutPriceMissingPersistenceException(
    val inventoryRef: String,
) : IllegalStateException("Inventory unit does not have a sellable price")

/** Сигнализирует, что checkout order не может смешивать разные валюты. */
class TicketingCheckoutCurrencyMismatchPersistenceException(
    val holdId: String,
    val expectedCurrency: String,
    val actualCurrency: String,
) : IllegalStateException("Checkout order currency mismatch")
