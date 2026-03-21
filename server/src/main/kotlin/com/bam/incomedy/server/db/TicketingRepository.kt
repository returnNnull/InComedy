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
 * Сохраненный checkout session для внешнего PSP handoff поверх `TicketOrder`.
 *
 * @property id Идентификатор checkout session.
 * @property orderId Идентификатор ticket order-а.
 * @property provider Активный PSP provider wire-name.
 * @property status Текущее состояние checkout session.
 * @property providerPaymentId Идентификатор платежа во внешнем провайдере.
 * @property providerStatus Сырым wire-значением зафиксированный статус, возвращенный провайдером.
 * @property confirmationUrl Redirect URL для клиента.
 * @property returnUrl Merchant-controlled URL возврата из PSP.
 * @property checkoutExpiresAt Момент истечения связанного order lock-а.
 */
data class StoredTicketCheckoutSession(
    val id: String,
    val orderId: String,
    val provider: String,
    val status: String,
    val providerPaymentId: String,
    val providerStatus: String,
    val confirmationUrl: String,
    val returnUrl: String,
    val checkoutExpiresAt: OffsetDateTime,
)

/**
 * Объединенное состояние checkout order-а и связанной PSP session.
 *
 * @property order Текущее состояние локального заказа.
 * @property session Текущее состояние внешней checkout session.
 */
data class StoredTicketCheckoutState(
    val order: StoredTicketOrder,
    val session: StoredTicketCheckoutSession,
)

/**
 * Выданный билет поверх оплаченного заказа.
 *
 * @property id Идентификатор билета.
 * @property orderId Идентификатор заказа-владельца.
 * @property eventId Идентификатор события.
 * @property inventoryUnitId Идентификатор inventory unit, из которой возник билет.
 * @property inventoryRef Стабильная ссылка на место/слот.
 * @property label Человекочитаемая подпись билета.
 * @property status Текущее состояние билета.
 * @property qrPayload Непрозрачная строка для QR-кода.
 * @property issuedAt Момент выпуска билета.
 * @property checkedInAt Момент прохода на входе, если он уже зафиксирован.
 * @property checkedInByUserId Идентификатор сотрудника, который зафиксировал проход.
 */
data class StoredIssuedTicket(
    val id: String,
    val orderId: String,
    val eventId: String,
    val inventoryUnitId: String,
    val inventoryRef: String,
    val label: String,
    val status: String,
    val qrPayload: String,
    val issuedAt: OffsetDateTime,
    val checkedInAt: OffsetDateTime? = null,
    val checkedInByUserId: String? = null,
)

/**
 * Результат server-side обработки check-in.
 *
 * @property resultCode Низкокардинальный код результата (`checked_in` или `duplicate`).
 * @property ticket Актуальное состояние билета после обработки.
 */
data class StoredTicketCheckInResult(
    val resultCode: String,
    val ticket: StoredIssuedTicket,
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

    /** Загружает checkout order, предварительно expiring его при просрочке. */
    fun findTicketOrder(
        orderId: String,
        now: OffsetDateTime,
    ): StoredTicketOrder?

    /** Возвращает все билеты текущего пользователя, выпущенные по его оплаченным заказам. */
    fun listIssuedTickets(
        userId: String,
        now: OffsetDateTime,
    ): List<StoredIssuedTicket>

    /** Ищет билет по QR payload для check-in flow. */
    fun findIssuedTicketByQrPayload(
        qrPayload: String,
        now: OffsetDateTime,
    ): StoredIssuedTicket?

    /** Возвращает активный checkout session для order-а, если он уже был создан. */
    fun findTicketCheckoutSession(
        orderId: String,
    ): StoredTicketCheckoutSession?

    /** Возвращает checkout state по внешнему provider payment id. */
    fun findTicketCheckoutStateByProviderPaymentId(
        provider: String,
        providerPaymentId: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState?

    /** Создает новый checkout session либо возвращает уже существующий idempotently по order-у. */
    fun createTicketCheckoutSession(
        orderId: String,
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        confirmationUrl: String,
        returnUrl: String,
        checkoutExpiresAt: OffsetDateTime,
        now: OffsetDateTime,
    ): StoredTicketCheckoutSession

    /** Переводит checkout session в `waiting_for_capture`, не завершая локальный order. */
    fun markTicketCheckoutWaitingForCapture(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState?

    /** Подтверждает платеж и atomically переводит order/inventory в финальное paid/sold состояние. */
    fun markTicketCheckoutSucceeded(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState?

    /** Фиксирует отмененный платеж и освобождает inventory, если order еще не завершен оплатой. */
    fun markTicketCheckoutCanceled(
        provider: String,
        providerPaymentId: String,
        providerStatus: String,
        now: OffsetDateTime,
    ): StoredTicketCheckoutState?

    /** Идемпотентно отмечает билет как использованный на входе. */
    fun markIssuedTicketCheckedIn(
        ticketId: String,
        checkedInByUserId: String,
        now: OffsetDateTime,
    ): StoredTicketCheckInResult?

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

/** Сигнализирует, что успешный provider payment требует ручного recovery вместо автозавершения. */
class TicketingCheckoutPaymentRecoveryRequiredPersistenceException(
    val orderId: String,
    val reasonCode: String,
) : IllegalStateException("Checkout payment requires recovery")
