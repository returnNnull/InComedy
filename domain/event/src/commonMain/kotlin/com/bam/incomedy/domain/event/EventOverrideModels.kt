package com.bam.incomedy.domain.event

/**
 * Полное редактируемое состояние organizer event поверх frozen `EventHallSnapshot`.
 *
 * Draft не меняет сам snapshot и описывает только event-local слой: базовые поля события,
 * локальные ценовые зоны, назначение этих зон на snapshot targets и event-specific доступность.
 *
 * @property title Название события.
 * @property description Необязательное описание события.
 * @property startsAtIso RFC3339 timestamp начала события.
 * @property doorsOpenAtIso Необязательное время открытия дверей.
 * @property endsAtIso Необязательное время окончания.
 * @property currency Валюта event-local price zones и базовой карточки события.
 * @property visibility Публичность события.
 * @property priceZones Event-local ценовые зоны.
 * @property pricingAssignments Назначения ценовых зон на элементы frozen snapshot.
 * @property availabilityOverrides Event-local override доступности.
 */
data class EventUpdateDraft(
    val title: String,
    val description: String? = null,
    val startsAtIso: String,
    val doorsOpenAtIso: String? = null,
    val endsAtIso: String? = null,
    val currency: String = "RUB",
    val visibility: EventVisibility = EventVisibility.PUBLIC,
    val priceZones: List<EventPriceZone> = emptyList(),
    val pricingAssignments: List<EventPricingAssignment> = emptyList(),
    val availabilityOverrides: List<EventAvailabilityOverride> = emptyList(),
)

/**
 * Event-local ценовая зона.
 *
 * @property id Стабильный идентификатор зоны внутри события.
 * @property name Отображаемое название ценовой зоны.
 * @property priceMinor Цена в minor units.
 * @property currency Валюта зоны в ISO-4217.
 * @property salesStartAtIso Необязательное окно начала продаж для зоны.
 * @property salesEndAtIso Необязательное окно завершения продаж для зоны.
 * @property sourceTemplatePriceZoneId Необязательная ссылка на исходную template price zone.
 */
data class EventPriceZone(
    val id: String,
    val name: String,
    val priceMinor: Int,
    val currency: String,
    val salesStartAtIso: String? = null,
    val salesEndAtIso: String? = null,
    val sourceTemplatePriceZoneId: String? = null,
)

/**
 * Назначение event-local ценовой зоны на элемент frozen snapshot.
 *
 * @property targetType Тип элемента snapshot-а.
 * @property targetRef Стабильный идентификатор конкретного элемента внутри snapshot-а.
 * @property eventPriceZoneId Идентификатор event-local ценовой зоны.
 */
data class EventPricingAssignment(
    val targetType: EventOverrideTargetType,
    val targetRef: String,
    val eventPriceZoneId: String,
)

/**
 * Event-local override доступности конкретного snapshot target-а.
 *
 * @property targetType Тип элемента snapshot-а.
 * @property targetRef Стабильный идентификатор конкретного элемента внутри snapshot-а.
 * @property availabilityStatus Требуемое состояние доступности для этого события.
 */
data class EventAvailabilityOverride(
    val targetType: EventOverrideTargetType,
    val targetRef: String,
    val availabilityStatus: EventAvailabilityStatus,
)

/**
 * Поддерживаемые типы snapshot targets для event-local overrides.
 *
 * @property wireName Значение для API/persistence.
 */
enum class EventOverrideTargetType(
    val wireName: String,
) {
    SEAT("seat"),
    ROW("row"),
    ZONE("zone"),
    TABLE("table"),
    ;

    companion object {
        /** Восстанавливает тип target-а по wire-значению. */
        fun fromWireName(value: String): EventOverrideTargetType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Поддерживаемые состояния event-local доступности.
 *
 * @property wireName Значение для API/persistence.
 */
enum class EventAvailabilityStatus(
    val wireName: String,
) {
    AVAILABLE("available"),
    BLOCKED("blocked"),
    ;

    companion object {
        /** Восстанавливает статус доступности по wire-значению. */
        fun fromWireName(value: String): EventAvailabilityStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
