package com.bam.incomedy.domain.ticketing

/**
 * Контракт ticketing foundation slice-а.
 *
 * Сервис задает минимальную клиентскую поверхность для чтения инвентаря события, управления
 * короткоживущими hold-ами и создания provider-agnostic checkout order foundation.
 */
interface TicketingService {
    /** Возвращает публичный инвентарь события без пользовательской персонализации hold-ов. */
    suspend fun listPublicInventory(
        eventId: String,
    ): Result<List<InventoryUnit>>

    /** Возвращает текущий инвентарь события с учетом hold-ов текущего пользователя. */
    suspend fun listInventory(
        accessToken: String,
        eventId: String,
    ): Result<List<InventoryUnit>>

    /** Создает временный hold на одну inventory unit текущего события. */
    suspend fun createSeatHold(
        accessToken: String,
        eventId: String,
        inventoryRef: String,
    ): Result<SeatHold>

    /** Создает checkout order из активных hold-ов текущего пользователя. */
    suspend fun createTicketOrder(
        accessToken: String,
        eventId: String,
        holdIds: List<String>,
    ): Result<TicketOrder>

    /** Возвращает текущий статус checkout order-а текущего пользователя. */
    suspend fun getTicketOrder(
        accessToken: String,
        orderId: String,
    ): Result<TicketOrder>

    /** Стартует внешний checkout для уже созданного pending order-а текущего пользователя. */
    suspend fun startTicketCheckout(
        accessToken: String,
        eventId: String,
        orderId: String,
    ): Result<TicketCheckoutSession>

    /** Освобождает ранее созданный hold текущего пользователя. */
    suspend fun releaseSeatHold(
        accessToken: String,
        holdId: String,
    ): Result<SeatHold>
}

/**
 * Клиентское представление sellable inventory unit.
 *
 * @property id Идентификатор persistence-записи inventory unit.
 * @property eventId Идентификатор события-владельца.
 * @property inventoryRef Стабильная ссылка на sellable unit внутри события.
 * @property inventoryType Тип inventory unit.
 * @property snapshotTargetType Тип исходного snapshot target-а.
 * @property snapshotTargetRef Идентификатор исходного snapshot target-а.
 * @property label Человекочитаемая подпись sellable unit.
 * @property priceZoneId Разрешенный идентификатор ценовой зоны.
 * @property priceZoneName Человекочитаемое имя ценовой зоны.
 * @property priceMinor Цена в minor units, если она уже определена.
 * @property currency Валюта цены.
 * @property status Текущее состояние inventory unit.
 * @property activeHoldId Идентификатор hold-а только если unit удерживается текущим пользователем.
 * @property holdExpiresAtIso Момент истечения hold-а только для hold-а текущего пользователя.
 * @property heldByCurrentUser Признак того, что активный hold принадлежит текущему пользователю.
 */
data class InventoryUnit(
    val id: String,
    val eventId: String,
    val inventoryRef: String,
    val inventoryType: InventoryType,
    val snapshotTargetType: InventorySnapshotTargetType,
    val snapshotTargetRef: String,
    val label: String,
    val priceZoneId: String? = null,
    val priceZoneName: String? = null,
    val priceMinor: Int? = null,
    val currency: String,
    val status: InventoryStatus,
    val activeHoldId: String? = null,
    val holdExpiresAtIso: String? = null,
    val heldByCurrentUser: Boolean = false,
)

/**
 * Типы inventory unit для первого ticketing slice-а.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class InventoryType(
    val wireName: String,
) {
    SEAT("seat"),
    ZONE_SLOT("zone_slot"),
    TABLE_SEAT("table_seat"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): InventoryType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Тип исходного snapshot target-а, из которого была выведена inventory unit.
 *
 * @property wireName Значение для backend API.
 */
enum class InventorySnapshotTargetType(
    val wireName: String,
) {
    SEAT("seat"),
    ZONE("zone"),
    TABLE("table"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): InventorySnapshotTargetType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Текущее состояние inventory unit.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class InventoryStatus(
    val wireName: String,
) {
    AVAILABLE("available"),
    HELD("held"),
    PENDING_PAYMENT("pending_payment"),
    UNAVAILABLE("unavailable"),
    SOLD("sold"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): InventoryStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Активный или уже завершенный hold на inventory unit.
 *
 * @property id Идентификатор hold-а.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit в persistence.
 * @property inventoryRef Стабильная ссылка на inventory unit.
 * @property expiresAtIso RFC3339 timestamp истечения hold-а.
 * @property status Текущее состояние hold-а.
 */
data class SeatHold(
    val id: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val expiresAtIso: String,
    val status: SeatHoldStatus,
)

/**
 * Жизненный цикл hold-а.
 *
 * @property wireName Значение для backend API и persistence.
 */
enum class SeatHoldStatus(
    val wireName: String,
) {
    ACTIVE("active"),
    CONSUMED("consumed"),
    RELEASED("released"),
    EXPIRED("expired"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): SeatHoldStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
