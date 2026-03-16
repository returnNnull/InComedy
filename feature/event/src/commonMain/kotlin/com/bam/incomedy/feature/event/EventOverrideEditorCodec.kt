package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventAvailabilityOverride
import com.bam.incomedy.domain.event.EventAvailabilityStatus
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.event.OrganizerEvent

/**
 * Текстовый codec event-local override editor-а.
 *
 * Он дает Android/iOS компактный organizer UI без отдельного seat-map редактирования: override-ы
 * вводятся строками `id|name|...` и применяются поверх frozen `EventHallSnapshot`.
 */
object EventOverrideEditorCodec {
    /** Преобразует organizer event в текстовый editor input для update-формы. */
    fun fromEvent(event: OrganizerEvent): EventOverrideEditorInput {
        return EventOverrideEditorInput(
            priceZonesText = event.priceZones.joinToString("\n") { zone ->
                listOf(
                    zone.id,
                    zone.name,
                    zone.priceMinor.toString(),
                    zone.currency,
                    zone.salesStartAtIso.orEmpty(),
                    zone.salesEndAtIso.orEmpty(),
                    zone.sourceTemplatePriceZoneId.orEmpty(),
                ).joinToString("|")
            },
            pricingAssignmentsText = event.pricingAssignments.joinToString("\n") { assignment ->
                listOf(
                    assignment.targetType.wireName,
                    assignment.targetRef,
                    assignment.eventPriceZoneId,
                ).joinToString("|")
            },
            availabilityOverridesText = event.availabilityOverrides.joinToString("\n") { override ->
                listOf(
                    override.targetType.wireName,
                    override.targetRef,
                    override.availabilityStatus.wireName,
                ).joinToString("|")
            },
        )
    }

    /** Строит краткую summary-строку для event-local override state. */
    fun summary(event: OrganizerEvent): String {
        return listOf(
            "price zones: ${event.priceZones.size}",
            "pricing: ${event.pricingAssignments.size}",
            "availability: ${event.availabilityOverrides.size}",
        ).joinToString(" · ")
    }

    /** Строит подсказку по доступным snapshot targets для текстового editor-а. */
    fun targetHint(event: OrganizerEvent): String {
        return buildList {
            val seatRefs = event.hallSnapshot.layout.rows.flatMap { row -> row.seats }.map { seat -> seat.ref }
            val rowIds = event.hallSnapshot.layout.rows.map { row -> row.id }
            val zoneIds = event.hallSnapshot.layout.zones.map { zone -> zone.id }
            val tableIds = event.hallSnapshot.layout.tables.map { table -> table.id }
            seatRefs.takeIf(List<String>::isNotEmpty)?.let { add("seat: ${it.take(3).joinToString(", ")}") }
            rowIds.takeIf(List<String>::isNotEmpty)?.let { add("row: ${it.take(3).joinToString(", ")}") }
            zoneIds.takeIf(List<String>::isNotEmpty)?.let { add("zone: ${it.take(3).joinToString(", ")}") }
            tableIds.takeIf(List<String>::isNotEmpty)?.let { add("table: ${it.take(3).joinToString(", ")}") }
        }.joinToString(" · ")
    }

    /** Разбирает текстовые event-local price zones. */
    fun parsePriceZones(raw: String): List<EventPriceZone> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size in 4..7) {
                "Строка event price zone должна иметь формат id|name|priceMinor|currency|salesStartAt|salesEndAt|sourceTemplatePriceZoneId"
            }
            EventPriceZone(
                id = parts[0],
                name = parts[1],
                priceMinor = parts[2].toInt(),
                currency = parts[3].uppercase(),
                salesStartAtIso = parts.getOrNull(4)?.takeIf(String::isNotBlank),
                salesEndAtIso = parts.getOrNull(5)?.takeIf(String::isNotBlank),
                sourceTemplatePriceZoneId = parts.getOrNull(6)?.takeIf(String::isNotBlank),
            )
        }
    }

    /** Разбирает текстовые pricing assignments. */
    fun parsePricingAssignments(raw: String): List<EventPricingAssignment> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size == 3) {
                "Строка pricing assignment должна иметь формат targetType|targetRef|eventPriceZoneId"
            }
            EventPricingAssignment(
                targetType = EventOverrideTargetType.fromWireName(parts[0])
                    ?: error("Неизвестный targetType '${parts[0]}'"),
                targetRef = parts[1],
                eventPriceZoneId = parts[2],
            )
        }
    }

    /** Разбирает текстовые availability overrides. */
    fun parseAvailabilityOverrides(raw: String): List<EventAvailabilityOverride> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size == 3) {
                "Строка availability override должна иметь формат targetType|targetRef|availabilityStatus"
            }
            EventAvailabilityOverride(
                targetType = EventOverrideTargetType.fromWireName(parts[0])
                    ?: error("Неизвестный targetType '${parts[0]}'"),
                targetRef = parts[1],
                availabilityStatus = EventAvailabilityStatus.fromWireName(parts[2])
                    ?: error("Неизвестный availabilityStatus '${parts[2]}'"),
            )
        }
    }

    /** Возвращает только непустые строки editor input-а. */
    private fun nonEmptyLines(raw: String): List<String> {
        return raw.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
    }
}

/**
 * Текстовое состояние event-local override editor-а.
 *
 * @property priceZonesText Строки event price zones `id|name|priceMinor|currency|salesStartAt|salesEndAt|sourceTemplatePriceZoneId`.
 * @property pricingAssignmentsText Строки pricing assignments `targetType|targetRef|eventPriceZoneId`.
 * @property availabilityOverridesText Строки availability overrides `targetType|targetRef|availabilityStatus`.
 */
data class EventOverrideEditorInput(
    val priceZonesText: String = "",
    val pricingAssignmentsText: String = "",
    val availabilityOverridesText: String = "",
)
