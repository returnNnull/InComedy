package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallPriceZone
import com.bam.incomedy.domain.venue.HallRow
import com.bam.incomedy.domain.venue.HallSeat
import com.bam.incomedy.domain.venue.HallServiceArea
import com.bam.incomedy.domain.venue.HallStage
import com.bam.incomedy.domain.venue.HallTable
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.HallZone
import com.bam.incomedy.domain.venue.VenueDraftValidator

/**
 * Текстовый codec для builder v1.
 *
 * Он дает Android/iOS компактный UI-формат без сложного CAD-редактора: каждая сущность вводится
 * строками `id|name|...`, а codec отвечает за восстановление typed domain layout.
 */
object HallLayoutEditorCodec {
    /** Преобразует текстовый editor input в доменный hall template draft. */
    fun toDraft(
        name: String,
        status: HallTemplateStatus,
        input: HallLayoutEditorInput,
    ): Result<HallTemplateDraft> {
        return runCatching {
            val layout = HallLayout(
                stage = input.stageLabel.trim()
                    .takeIf(String::isNotBlank)
                    ?.let { HallStage(label = it) },
                priceZones = parsePriceZones(input.priceZonesText),
                zones = parseZones(input.zonesText),
                rows = parseRows(input.rowsText),
                tables = parseTables(input.tablesText),
                serviceAreas = parseServiceAreas(input.serviceAreasText),
                blockedSeatRefs = input.blockedSeatRefsText
                    .split(",", "\n")
                    .map(String::trim)
                    .filter(String::isNotBlank),
            )
            val draft = HallTemplateDraft(
                name = name.trim(),
                status = status,
                layout = layout,
            )
            val validationError = VenueDraftValidator.validateHallTemplateDraft(draft)
            if (validationError != null) {
                error(validationError)
            }
            draft
        }
    }

    /** Преобразует сохраненный template в текстовый editor input для редактирования. */
    fun fromTemplate(template: HallTemplate): HallLayoutEditorInput {
        return HallLayoutEditorInput(
            stageLabel = template.layout.stage?.label.orEmpty(),
            priceZonesText = template.layout.priceZones.joinToString("\n") { zone ->
                listOf(zone.id, zone.name, zone.defaultPriceMinor?.toString().orEmpty()).joinToString("|")
            },
            zonesText = template.layout.zones.joinToString("\n") { zone ->
                listOf(
                    zone.id,
                    zone.name,
                    zone.capacity.toString(),
                    zone.priceZoneId.orEmpty(),
                    zone.kind,
                ).joinToString("|")
            },
            rowsText = template.layout.rows.joinToString("\n") { row ->
                listOf(
                    row.id,
                    row.label,
                    row.seats.size.toString(),
                    row.priceZoneId.orEmpty(),
                ).joinToString("|")
            },
            tablesText = template.layout.tables.joinToString("\n") { table ->
                listOf(
                    table.id,
                    table.label,
                    table.seatCount.toString(),
                    table.priceZoneId.orEmpty(),
                ).joinToString("|")
            },
            serviceAreasText = template.layout.serviceAreas.joinToString("\n") { area ->
                listOf(area.id, area.name, area.kind).joinToString("|")
            },
            blockedSeatRefsText = template.layout.blockedSeatRefs.joinToString(", "),
        )
    }

    /** Строит краткую summary-строку для карточки шаблона. */
    fun summary(layout: HallLayout): String {
        return buildList {
            layout.stage?.let { add("сцена") }
            if (layout.priceZones.isNotEmpty()) add("ценовых зон: ${layout.priceZones.size}")
            if (layout.zones.isNotEmpty()) add("секторов: ${layout.zones.size}")
            if (layout.rows.isNotEmpty()) add("рядов: ${layout.rows.size}")
            if (layout.tables.isNotEmpty()) add("столов: ${layout.tables.size}")
            if (layout.serviceAreas.isNotEmpty()) add("служебных зон: ${layout.serviceAreas.size}")
            if (layout.blockedSeatRefs.isNotEmpty()) add("blocked seats: ${layout.blockedSeatRefs.size}")
        }.joinToString(" · ")
    }

    /** Разбирает строки ценовых зон формата `id|name|defaultPriceMinor`. */
    private fun parsePriceZones(raw: String): List<HallPriceZone> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size in 2..3) { "Строка ценовой зоны должна иметь формат id|name|defaultPriceMinor" }
            HallPriceZone(
                id = parts[0],
                name = parts[1],
                defaultPriceMinor = parts.getOrNull(2)?.takeIf(String::isNotBlank)?.toInt(),
            )
        }
    }

    /** Разбирает строки standing/sector зон формата `id|name|capacity|priceZoneId|kind`. */
    private fun parseZones(raw: String): List<HallZone> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size in 3..5) { "Строка сектора должна иметь формат id|name|capacity|priceZoneId|kind" }
            HallZone(
                id = parts[0],
                name = parts[1],
                capacity = parts[2].toInt(),
                priceZoneId = parts.getOrNull(3)?.takeIf(String::isNotBlank),
                kind = parts.getOrNull(4)?.takeIf(String::isNotBlank) ?: "standing",
            )
        }
    }

    /** Разбирает строки рядов формата `rowId|rowLabel|seatCount|priceZoneId`. */
    private fun parseRows(raw: String): List<HallRow> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size in 3..4) { "Строка ряда должна иметь формат rowId|rowLabel|seatCount|priceZoneId" }
            val rowId = parts[0]
            val rowLabel = parts[1]
            val seatCount = parts[2].toInt()
            HallRow(
                id = rowId,
                label = rowLabel,
                seats = (1..seatCount).map { index ->
                    HallSeat(
                        ref = "$rowId-$index",
                        label = index.toString(),
                    )
                },
                priceZoneId = parts.getOrNull(3)?.takeIf(String::isNotBlank),
            )
        }
    }

    /** Разбирает строки столов формата `tableId|label|seatCount|priceZoneId`. */
    private fun parseTables(raw: String): List<HallTable> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size in 3..4) { "Строка стола должна иметь формат tableId|label|seatCount|priceZoneId" }
            HallTable(
                id = parts[0],
                label = parts[1],
                seatCount = parts[2].toInt(),
                priceZoneId = parts.getOrNull(3)?.takeIf(String::isNotBlank),
            )
        }
    }

    /** Разбирает строки service areas формата `areaId|name|kind`. */
    private fun parseServiceAreas(raw: String): List<HallServiceArea> {
        return nonEmptyLines(raw).map { line ->
            val parts = line.split("|").map(String::trim)
            require(parts.size == 3) { "Строка служебной зоны должна иметь формат areaId|name|kind" }
            HallServiceArea(
                id = parts[0],
                name = parts[1],
                kind = parts[2],
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
 * Текстовое состояние редактора hall template builder v1.
 *
 * @property stageLabel Название сцены.
 * @property priceZonesText Строки ценовых зон `id|name|defaultPriceMinor`.
 * @property zonesText Строки standing/sector зон `id|name|capacity|priceZoneId|kind`.
 * @property rowsText Строки рядов `rowId|rowLabel|seatCount|priceZoneId`.
 * @property tablesText Строки столов `tableId|label|seatCount|priceZoneId`.
 * @property serviceAreasText Строки служебных зон `areaId|name|kind`.
 * @property blockedSeatRefsText Список blocked seat refs через запятую.
 */
data class HallLayoutEditorInput(
    val stageLabel: String = "",
    val priceZonesText: String = "",
    val zonesText: String = "",
    val rowsText: String = "",
    val tablesText: String = "",
    val serviceAreasText: String = "",
    val blockedSeatRefsText: String = "",
)
