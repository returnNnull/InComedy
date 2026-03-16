package com.bam.incomedy.domain.ticketing

import com.bam.incomedy.domain.event.EventAvailabilityOverride
import com.bam.incomedy.domain.event.EventAvailabilityStatus
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Проверяет derivation sellable inventory units из snapshot layout и event-local overrides.
 */
class TicketingInventoryCompilerTest {
    /** Проверяет seat/zone/table derivation, availability precedence и pricing fallback chain. */
    @Test
    fun `compiler derives inventory units with resolved pricing and availability`() {
        val derived = TicketingInventoryCompiler.compile(
            eventId = "event-1",
            currency = "RUB",
            layout = HallLayout(
                priceZones = listOf(
                    HallPriceZone(id = "template-standard", name = "Standard", defaultPriceMinor = 1500),
                    HallPriceZone(id = "template-vip", name = "VIP", defaultPriceMinor = 3200),
                ),
                zones = listOf(
                    HallZone(
                        id = "zone-1",
                        name = "Балкон",
                        capacity = 2,
                        priceZoneId = "template-standard",
                    ),
                ),
                rows = listOf(
                    HallRow(
                        id = "row-a",
                        label = "A",
                        seats = listOf(
                            HallSeat(ref = "seat-a-1", label = "1"),
                            HallSeat(ref = "seat-a-2", label = "2"),
                        ),
                        priceZoneId = "template-standard",
                    ),
                ),
                tables = listOf(
                    HallTable(
                        id = "table-1",
                        label = "VIP 1",
                        seatCount = 2,
                        priceZoneId = "template-vip",
                    ),
                ),
                blockedSeatRefs = listOf("seat-a-1"),
            ),
            priceZones = listOf(
                EventPriceZone(
                    id = "event-standard",
                    name = "Standard Event",
                    priceMinor = 1900,
                    currency = "RUB",
                    sourceTemplatePriceZoneId = "template-standard",
                ),
                EventPriceZone(
                    id = "event-vip",
                    name = "VIP Event",
                    priceMinor = 4100,
                    currency = "RUB",
                    sourceTemplatePriceZoneId = "template-vip",
                ),
                EventPriceZone(
                    id = "event-seat-direct",
                    name = "Seat Direct",
                    priceMinor = 5000,
                    currency = "RUB",
                ),
            ),
            pricingAssignments = listOf(
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.SEAT,
                    targetRef = "seat-a-2",
                    eventPriceZoneId = "event-seat-direct",
                ),
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.TABLE,
                    targetRef = "table-1",
                    eventPriceZoneId = "event-vip",
                ),
            ),
            availabilityOverrides = listOf(
                EventAvailabilityOverride(
                    targetType = EventOverrideTargetType.ROW,
                    targetRef = "row-a",
                    availabilityStatus = EventAvailabilityStatus.BLOCKED,
                ),
                EventAvailabilityOverride(
                    targetType = EventOverrideTargetType.SEAT,
                    targetRef = "seat-a-2",
                    availabilityStatus = EventAvailabilityStatus.AVAILABLE,
                ),
                EventAvailabilityOverride(
                    targetType = EventOverrideTargetType.ZONE,
                    targetRef = "zone-1",
                    availabilityStatus = EventAvailabilityStatus.BLOCKED,
                ),
            ),
        )

        assertEquals(6, derived.size)

        val blockedSeat = derived.first { it.inventoryRef == "seat:seat-a-1" }
        assertEquals(InventoryStatus.UNAVAILABLE, blockedSeat.baseStatus)
        assertEquals("event-standard", blockedSeat.priceZoneId)
        assertEquals(1900, blockedSeat.priceMinor)

        val availableSeat = derived.first { it.inventoryRef == "seat:seat-a-2" }
        assertEquals(InventoryStatus.AVAILABLE, availableSeat.baseStatus)
        assertEquals("event-seat-direct", availableSeat.priceZoneId)
        assertEquals(5000, availableSeat.priceMinor)

        val zoneSlot = derived.first { it.inventoryRef == "zone:zone-1:slot:1" }
        assertEquals(InventoryType.ZONE_SLOT, zoneSlot.inventoryType)
        assertEquals(InventoryStatus.UNAVAILABLE, zoneSlot.baseStatus)

        val tableSeat = derived.first { it.inventoryRef == "table:table-1:seat:2" }
        assertEquals(InventoryType.TABLE_SEAT, tableSeat.inventoryType)
        assertEquals(InventoryStatus.AVAILABLE, tableSeat.baseStatus)
        assertEquals("event-vip", tableSeat.priceZoneId)
        assertEquals(4100, tableSeat.priceMinor)
    }

    /** Проверяет fallback к template price zone, если event-local pricing не задан. */
    @Test
    fun `compiler falls back to template pricing when event pricing is absent`() {
        val derived = TicketingInventoryCompiler.compile(
            eventId = "event-2",
            currency = "RUB",
            layout = HallLayout(
                priceZones = listOf(
                    HallPriceZone(id = "template-standard", name = "Standard", defaultPriceMinor = 1200),
                ),
                rows = listOf(
                    HallRow(
                        id = "row-b",
                        label = "B",
                        seats = listOf(HallSeat(ref = "seat-b-1", label = "1")),
                        priceZoneId = "template-standard",
                    ),
                ),
            ),
            priceZones = emptyList(),
            pricingAssignments = emptyList(),
            availabilityOverrides = emptyList(),
        )

        val unit = assertNotNull(derived.singleOrNull())
        assertEquals("template-standard", unit.priceZoneId)
        assertEquals("Standard", unit.priceZoneName)
        assertEquals(1200, unit.priceMinor)
        assertEquals(InventoryStatus.AVAILABLE, unit.baseStatus)
    }
}
