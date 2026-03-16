package com.bam.incomedy.domain.ticketing

import com.bam.incomedy.domain.event.EventAvailabilityOverride
import com.bam.incomedy.domain.event.EventAvailabilityStatus
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallZone

/**
 * Компилирует sellable inventory units из frozen `EventHallSnapshot` и event-local overrides.
 *
 * Компилятор выделен в domain-слой, чтобы одна и та же логика derivation использовалась в backend
 * persistence и оставалась доступной для будущего клиентского preview/check-out flow.
 */
object TicketingInventoryCompiler {
    /** Собирает ticketing blueprint из полного organizer event состояния. */
    fun compile(event: OrganizerEvent): List<DerivedInventoryUnit> {
        return compile(
            eventId = event.id,
            currency = event.currency,
            layout = event.hallSnapshot.layout,
            priceZones = event.priceZones,
            pricingAssignments = event.pricingAssignments,
            availabilityOverrides = event.availabilityOverrides,
        )
    }

    /**
     * Собирает список sellable units из snapshot layout и event-local pricing/availability слоя.
     *
     * Для standing zone и table seats компилятор создает дискретные слоты, чтобы hold-механика
     * могла работать без отдельной basket-модели уже в первом foundation slice-е.
     */
    fun compile(
        eventId: String,
        currency: String,
        layout: HallLayout,
        priceZones: List<EventPriceZone>,
        pricingAssignments: List<EventPricingAssignment>,
        availabilityOverrides: List<EventAvailabilityOverride>,
    ): List<DerivedInventoryUnit> {
        val pricingResolver = PricingResolver(
            eventCurrency = currency,
            templatePriceZones = layout.priceZones,
            eventPriceZones = priceZones,
            assignments = pricingAssignments,
        )
        val availabilityResolver = AvailabilityResolver(
            blockedSeatRefs = layout.blockedSeatRefs.toSet(),
            overrides = availabilityOverrides,
        )
        return buildList {
            layout.rows.forEach { row ->
                addAll(
                    compileRowSeats(
                        eventId = eventId,
                        row = row,
                        pricingResolver = pricingResolver,
                        availabilityResolver = availabilityResolver,
                    ),
                )
            }
            layout.zones.forEach { zone ->
                addAll(
                    compileZoneSlots(
                        eventId = eventId,
                        zone = zone,
                        pricingResolver = pricingResolver,
                        availabilityResolver = availabilityResolver,
                    ),
                )
            }
            layout.tables.forEach { table ->
                addAll(
                    compileTableSeats(
                        eventId = eventId,
                        table = table,
                        pricingResolver = pricingResolver,
                        availabilityResolver = availabilityResolver,
                    ),
                )
            }
        }
    }

    /** Создает inventory units для ряда с дискретными местами. */
    private fun compileRowSeats(
        eventId: String,
        row: HallRow,
        pricingResolver: PricingResolver,
        availabilityResolver: AvailabilityResolver,
    ): List<DerivedInventoryUnit> {
        val rowAvailability = availabilityResolver.resolveRowAvailability(row.id)
        val rowPricing = pricingResolver.resolveRowPricing(row)
        return row.seats.map { seat ->
            val seatAvailability = availabilityResolver.resolveSeatAvailability(
                seatRef = seat.ref,
                rowAvailability = rowAvailability,
            )
            val pricing = pricingResolver.resolveSeatPricing(
                seatRef = seat.ref,
                rowPricing = rowPricing,
            )
            DerivedInventoryUnit(
                eventId = eventId,
                inventoryRef = "seat:${seat.ref}",
                inventoryType = InventoryType.SEAT,
                snapshotTargetType = InventorySnapshotTargetType.SEAT,
                snapshotTargetRef = seat.ref,
                label = "Ряд ${row.label}, место ${seat.label}",
                priceZoneId = pricing.priceZoneId,
                priceZoneName = pricing.priceZoneName,
                priceMinor = pricing.priceMinor,
                currency = pricing.currency,
                baseStatus = if (seatAvailability == EventAvailabilityStatus.AVAILABLE) {
                    InventoryStatus.AVAILABLE
                } else {
                    InventoryStatus.UNAVAILABLE
                },
            )
        }
    }

    /** Создает дискретные standing zone slots для hold-механики. */
    private fun compileZoneSlots(
        eventId: String,
        zone: HallZone,
        pricingResolver: PricingResolver,
        availabilityResolver: AvailabilityResolver,
    ): List<DerivedInventoryUnit> {
        val availability = availabilityResolver.resolveZoneAvailability(zone.id)
        val pricing = pricingResolver.resolveZonePricing(zone)
        return (1..zone.capacity).map { slotIndex ->
            DerivedInventoryUnit(
                eventId = eventId,
                inventoryRef = "zone:${zone.id}:slot:$slotIndex",
                inventoryType = InventoryType.ZONE_SLOT,
                snapshotTargetType = InventorySnapshotTargetType.ZONE,
                snapshotTargetRef = zone.id,
                label = "${zone.name}, слот $slotIndex",
                priceZoneId = pricing.priceZoneId,
                priceZoneName = pricing.priceZoneName,
                priceMinor = pricing.priceMinor,
                currency = pricing.currency,
                baseStatus = if (availability == EventAvailabilityStatus.AVAILABLE) {
                    InventoryStatus.AVAILABLE
                } else {
                    InventoryStatus.UNAVAILABLE
                },
            )
        }
    }

    /** Создает дискретные table seats на основе агрегированного snapshot table target-а. */
    private fun compileTableSeats(
        eventId: String,
        table: HallTable,
        pricingResolver: PricingResolver,
        availabilityResolver: AvailabilityResolver,
    ): List<DerivedInventoryUnit> {
        val availability = availabilityResolver.resolveTableAvailability(table.id)
        val pricing = pricingResolver.resolveTablePricing(table)
        return (1..table.seatCount).map { seatIndex ->
            DerivedInventoryUnit(
                eventId = eventId,
                inventoryRef = "table:${table.id}:seat:$seatIndex",
                inventoryType = InventoryType.TABLE_SEAT,
                snapshotTargetType = InventorySnapshotTargetType.TABLE,
                snapshotTargetRef = table.id,
                label = "${table.label}, место $seatIndex",
                priceZoneId = pricing.priceZoneId,
                priceZoneName = pricing.priceZoneName,
                priceMinor = pricing.priceMinor,
                currency = pricing.currency,
                baseStatus = if (availability == EventAvailabilityStatus.AVAILABLE) {
                    InventoryStatus.AVAILABLE
                } else {
                    InventoryStatus.UNAVAILABLE
                },
            )
        }
    }

    /** Разрешает итоговое availability для snapshot target-ов. */
    private class AvailabilityResolver(
        blockedSeatRefs: Set<String>,
        overrides: List<EventAvailabilityOverride>,
    ) {
        /** Базово заблокированные места из hall snapshot. */
        private val blockedSeatRefs = blockedSeatRefs

        /** Быстрый доступ к event-local override-ам по типу и target ref. */
        private val overridesByTarget = overrides.associateBy { override ->
            override.targetType to override.targetRef
        }

        /** Возвращает availability конкретного seat-а с приоритетом seat override над row/base. */
        fun resolveSeatAvailability(
            seatRef: String,
            rowAvailability: EventAvailabilityStatus,
        ): EventAvailabilityStatus {
            return overridesByTarget[EventOverrideTargetType.SEAT to seatRef]?.availabilityStatus
                ?: if (seatRef in blockedSeatRefs) {
                    EventAvailabilityStatus.BLOCKED
                } else {
                    rowAvailability
                }
        }

        /** Возвращает availability ряда, если на него назначен override. */
        fun resolveRowAvailability(rowId: String): EventAvailabilityStatus {
            return overridesByTarget[EventOverrideTargetType.ROW to rowId]?.availabilityStatus
                ?: EventAvailabilityStatus.AVAILABLE
        }

        /** Возвращает availability standing zone. */
        fun resolveZoneAvailability(zoneId: String): EventAvailabilityStatus {
            return overridesByTarget[EventOverrideTargetType.ZONE to zoneId]?.availabilityStatus
                ?: EventAvailabilityStatus.AVAILABLE
        }

        /** Возвращает availability table target-а. */
        fun resolveTableAvailability(tableId: String): EventAvailabilityStatus {
            return overridesByTarget[EventOverrideTargetType.TABLE to tableId]?.availabilityStatus
                ?: EventAvailabilityStatus.AVAILABLE
        }
    }

    /** Разрешает итоговую цену и ценовую зону для конкретного snapshot target-а. */
    private class PricingResolver(
        private val eventCurrency: String,
        templatePriceZones: List<HallPriceZone>,
        eventPriceZones: List<EventPriceZone>,
        assignments: List<EventPricingAssignment>,
    ) {
        /** Price zone-ы snapshot template по их id. */
        private val templatePriceZonesById = templatePriceZones.associateBy(HallPriceZone::id)

        /** Event-local price zone-ы по id. */
        private val eventPriceZonesById = eventPriceZones.associateBy(EventPriceZone::id)

        /** Event-local price zone-ы, привязанные к template price zone. */
        private val eventPriceZonesBySourceTemplateId = eventPriceZones
            .filter { !it.sourceTemplatePriceZoneId.isNullOrBlank() }
            .associateBy { requireNotNull(it.sourceTemplatePriceZoneId) }

        /** Прямые pricing assignments по snapshot target-у. */
        private val assignmentsByTarget = assignments.associateBy { assignment ->
            assignment.targetType to assignment.targetRef
        }

        /** Возвращает pricing для seat-а с учетом seat -> row -> template fallback chain. */
        fun resolveSeatPricing(
            seatRef: String,
            rowPricing: ResolvedPricing,
        ): ResolvedPricing {
            return assignmentsByTarget[EventOverrideTargetType.SEAT to seatRef]
                ?.let(::resolveEventAssignment)
                ?: rowPricing
        }

        /** Возвращает pricing для ряда. */
        fun resolveRowPricing(row: HallRow): ResolvedPricing {
            return assignmentsByTarget[EventOverrideTargetType.ROW to row.id]
                ?.let(::resolveEventAssignment)
                ?: resolveTemplateFallback(row.priceZoneId)
        }

        /** Возвращает pricing для standing zone. */
        fun resolveZonePricing(zone: HallZone): ResolvedPricing {
            return assignmentsByTarget[EventOverrideTargetType.ZONE to zone.id]
                ?.let(::resolveEventAssignment)
                ?: resolveTemplateFallback(zone.priceZoneId)
        }

        /** Возвращает pricing для table target-а. */
        fun resolveTablePricing(table: HallTable): ResolvedPricing {
            return assignmentsByTarget[EventOverrideTargetType.TABLE to table.id]
                ?.let(::resolveEventAssignment)
                ?: resolveTemplateFallback(table.priceZoneId)
        }

        /** Разрешает прямое назначение event-local price zone. */
        private fun resolveEventAssignment(
            assignment: EventPricingAssignment,
        ): ResolvedPricing {
            val zone = eventPriceZonesById[assignment.eventPriceZoneId]
            return if (zone != null) {
                ResolvedPricing(
                    priceZoneId = zone.id,
                    priceZoneName = zone.name,
                    priceMinor = zone.priceMinor,
                    currency = zone.currency,
                )
            } else {
                ResolvedPricing(currency = eventCurrency)
            }
        }

        /** Разрешает pricing через event-local override или template price zone fallback. */
        private fun resolveTemplateFallback(templatePriceZoneId: String?): ResolvedPricing {
            if (templatePriceZoneId.isNullOrBlank()) {
                return ResolvedPricing(currency = eventCurrency)
            }
            val eventOverride = eventPriceZonesBySourceTemplateId[templatePriceZoneId]
            if (eventOverride != null) {
                return ResolvedPricing(
                    priceZoneId = eventOverride.id,
                    priceZoneName = eventOverride.name,
                    priceMinor = eventOverride.priceMinor,
                    currency = eventOverride.currency,
                )
            }
            val templatePriceZone = templatePriceZonesById[templatePriceZoneId]
            return ResolvedPricing(
                priceZoneId = templatePriceZone?.id,
                priceZoneName = templatePriceZone?.name,
                priceMinor = templatePriceZone?.defaultPriceMinor,
                currency = eventCurrency,
            )
        }
    }
}

/**
 * Domain-blueprint для дальнейшего persistence или transport mapping.
 *
 * @property eventId Идентификатор события.
 * @property inventoryRef Стабильная ссылка на inventory unit.
 * @property inventoryType Тип inventory unit.
 * @property snapshotTargetType Тип исходного snapshot target-а.
 * @property snapshotTargetRef Идентификатор исходного snapshot target-а.
 * @property label Человекочитаемая подпись inventory unit.
 * @property priceZoneId Разрешенная ценовая зона.
 * @property priceZoneName Имя ценовой зоны.
 * @property priceMinor Цена в minor units, если она уже определена.
 * @property currency Валюта цены.
 * @property baseStatus Базовая доступность, на которую должен откатываться unit после release/expiry hold-а.
 */
data class DerivedInventoryUnit(
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
    val baseStatus: InventoryStatus,
)

/**
 * Внутренний carrier уже разрешенной цены для конкретной inventory unit.
 *
 * @property priceZoneId Идентификатор ценовой зоны.
 * @property priceZoneName Отображаемое название зоны.
 * @property priceMinor Цена в minor units.
 * @property currency Валюта.
 */
private data class ResolvedPricing(
    val priceZoneId: String? = null,
    val priceZoneName: String? = null,
    val priceMinor: Int? = null,
    val currency: String,
)
