package com.bam.incomedy.domain.event

import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit-тесты shared validator-а event-local overrides.
 */
class EventOverrideValidatorTest {
    /** Проверяет happy path для корректного override draft-а. */
    @Test
    fun validEventUpdateDraftPassesValidation() {
        val draft = EventUpdateDraft(
            title = "Late Night Standup",
            startsAtIso = "2026-04-01T19:00:00+03:00",
            currency = "RUB",
            priceZones = listOf(
                EventPriceZone(
                    id = "event-vip",
                    name = "VIP",
                    priceMinor = 3_500,
                    currency = "RUB",
                ),
            ),
            pricingAssignments = listOf(
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.ROW,
                    targetRef = "row-a",
                    eventPriceZoneId = "event-vip",
                ),
            ),
            availabilityOverrides = listOf(
                EventAvailabilityOverride(
                    targetType = EventOverrideTargetType.SEAT,
                    targetRef = "row-a-2",
                    availabilityStatus = EventAvailabilityStatus.BLOCKED,
                ),
            ),
        )

        assertNull(EventOverrideValidator.validateEventUpdateDraft(draft, snapshotLayout()))
    }

    /** Проверяет, что validator блокирует override на неизвестный snapshot target. */
    @Test
    fun unknownSnapshotTargetFailsValidation() {
        val draft = EventUpdateDraft(
            title = "Late Night Standup",
            startsAtIso = "2026-04-01T19:00:00+03:00",
            currency = "RUB",
            priceZones = listOf(
                EventPriceZone(
                    id = "event-main",
                    name = "Main",
                    priceMinor = 2_000,
                    currency = "RUB",
                ),
            ),
            pricingAssignments = listOf(
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.SEAT,
                    targetRef = "missing-seat",
                    eventPriceZoneId = "event-main",
                ),
            ),
        )

        assertEquals(
            "Каждое ценовое назначение должно ссылаться на существующие event price zone и snapshot target",
            EventOverrideValidator.validateEventUpdateDraft(draft, snapshotLayout()),
        )
    }

    /** Проверяет, что validator блокирует повторное назначение для одного и того же target-а. */
    @Test
    fun duplicatedTargetAssignmentFailsValidation() {
        val draft = EventUpdateDraft(
            title = "Late Night Standup",
            startsAtIso = "2026-04-01T19:00:00+03:00",
            currency = "RUB",
            priceZones = listOf(
                EventPriceZone(
                    id = "event-main",
                    name = "Main",
                    priceMinor = 2_000,
                    currency = "RUB",
                ),
                EventPriceZone(
                    id = "event-vip",
                    name = "VIP",
                    priceMinor = 3_000,
                    currency = "RUB",
                ),
            ),
            pricingAssignments = listOf(
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.ROW,
                    targetRef = "row-a",
                    eventPriceZoneId = "event-main",
                ),
                EventPricingAssignment(
                    targetType = EventOverrideTargetType.ROW,
                    targetRef = "row-a",
                    eventPriceZoneId = "event-vip",
                ),
            ),
        )

        assertEquals(
            "Один snapshot target не может иметь несколько event price zone assignments",
            EventOverrideValidator.validateEventUpdateDraft(draft, snapshotLayout()),
        )
    }

    /** Возвращает компактный snapshot layout для validator tests. */
    private fun snapshotLayout(): HallLayout {
        return HallLayout(
            zones = listOf(
                HallZone(
                    id = "zone-left",
                    name = "Левый сектор",
                    capacity = 40,
                    kind = "sector",
                ),
            ),
            rows = listOf(
                HallRow(
                    id = "row-a",
                    label = "A",
                    seats = listOf(
                        HallSeat(ref = "row-a-1", label = "1"),
                        HallSeat(ref = "row-a-2", label = "2"),
                    ),
                ),
            ),
            tables = listOf(
                HallTable(
                    id = "table-1",
                    label = "T1",
                    seatCount = 4,
                ),
            ),
        )
    }
}
