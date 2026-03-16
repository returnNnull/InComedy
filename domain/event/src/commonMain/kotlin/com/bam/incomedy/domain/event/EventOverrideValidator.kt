package com.bam.incomedy.domain.event

import com.bam.incomedy.domain.venue.HallLayout

/**
 * Набор бизнес-валидаций для event-local pricing и availability overrides.
 *
 * Валидатор держит organizer event editor в одном инвариантном поле для Android/iOS, shared MVI и
 * backend routes, не позволяя event slice-у случайно перейти в ticketing inventory semantics.
 */
object EventOverrideValidator {
    /** Проверяет корректность event update draft поверх frozen snapshot. */
    fun validateEventUpdateDraft(
        draft: EventUpdateDraft,
        snapshotLayout: HallLayout,
    ): String? {
        if (draft.title.trim().length !in 3..120) return "Название события должно быть от 3 до 120 символов"
        if ((draft.description?.trim()?.length ?: 0) > 2_000) return "Описание события не должно превышать 2000 символов"
        if (draft.startsAtIso.trim().isEmpty()) return "Нужно указать время начала события"
        if (draft.startsAtIso.trim().length !in 10..64) return "Некорректный формат времени начала события"
        if (draft.doorsOpenAtIso?.trim()?.length ?: 0 > 64) return "Некорректный формат времени открытия дверей"
        if (draft.endsAtIso?.trim()?.length ?: 0 > 64) return "Некорректный формат времени окончания"
        val currency = draft.currency.trim()
        if (!currency.matches(Regex("[A-Z]{3}"))) return "Валюта события должна быть в формате ISO-4217"

        if (duplicatedId(draft.priceZones.map(EventPriceZone::id)) != null) {
            return "Идентификаторы event price zones не должны повторяться"
        }
        val invalidPriceZone = draft.priceZones.firstOrNull { zone ->
            zone.id.trim().length !in 1..64 ||
                zone.name.trim().length !in 2..80 ||
                zone.priceMinor < 0 ||
                !zone.currency.trim().matches(Regex("[A-Z]{3}")) ||
                (zone.salesStartAtIso?.trim()?.length ?: 0 > 64) ||
                (zone.salesEndAtIso?.trim()?.length ?: 0 > 64) ||
                (zone.sourceTemplatePriceZoneId?.trim()?.length ?: 0 > 64)
        }
        if (invalidPriceZone != null) {
            return "Каждая event price zone должна иметь id, имя, неотрицательную цену и корректную валюту"
        }

        val knownPriceZoneIds = draft.priceZones.mapTo(linkedSetOf(), EventPriceZone::id)
        val knownTargets = snapshotTargets(snapshotLayout)

        val duplicatedAssignmentTarget = duplicatedTargetKey(
            draft.pricingAssignments.map { assignment -> assignment.targetType to assignment.targetRef },
        )
        if (duplicatedAssignmentTarget != null) {
            return "Один snapshot target не может иметь несколько event price zone assignments"
        }
        val invalidAssignment = draft.pricingAssignments.firstOrNull { assignment ->
            assignment.targetRef.trim().isEmpty() ||
                assignment.eventPriceZoneId.trim().isEmpty() ||
                assignment.eventPriceZoneId !in knownPriceZoneIds ||
                assignment.targetKey() !in knownTargets
        }
        if (invalidAssignment != null) {
            return "Каждое ценовое назначение должно ссылаться на существующие event price zone и snapshot target"
        }

        val duplicatedAvailabilityTarget = duplicatedTargetKey(
            draft.availabilityOverrides.map { override -> override.targetType to override.targetRef },
        )
        if (duplicatedAvailabilityTarget != null) {
            return "Один snapshot target не может иметь несколько availability overrides"
        }
        val invalidAvailabilityOverride = draft.availabilityOverrides.firstOrNull { override ->
            override.targetRef.trim().isEmpty() || override.targetKey() !in knownTargets
        }
        if (invalidAvailabilityOverride != null) {
            return "Каждый availability override должен ссылаться на существующий snapshot target"
        }

        return null
    }

    /** Собирает множество поддерживаемых snapshot targets из frozen layout. */
    private fun snapshotTargets(layout: HallLayout): Set<Pair<EventOverrideTargetType, String>> {
        return buildSet {
            layout.rows.forEach { row ->
                add(EventOverrideTargetType.ROW to row.id)
                row.seats.forEach { seat -> add(EventOverrideTargetType.SEAT to seat.ref) }
            }
            layout.zones.forEach { zone -> add(EventOverrideTargetType.ZONE to zone.id) }
            layout.tables.forEach { table -> add(EventOverrideTargetType.TABLE to table.id) }
        }
    }

    /** Возвращает первый повторяющийся id, если он есть. */
    private fun duplicatedId(ids: List<String>): String? {
        val seen = linkedSetOf<String>()
        return ids.firstOrNull { !seen.add(it) }
    }

    /** Возвращает первый повторяющийся snapshot target. */
    private fun duplicatedTargetKey(keys: List<Pair<EventOverrideTargetType, String>>): Pair<EventOverrideTargetType, String>? {
        val seen = linkedSetOf<Pair<EventOverrideTargetType, String>>()
        return keys.firstOrNull { !seen.add(it.first to it.second.trim()) }
            ?.let { it.first to it.second.trim() }
    }

    /** Строит canonical target key для validator checks. */
    private fun EventPricingAssignment.targetKey(): Pair<EventOverrideTargetType, String> {
        return targetType to targetRef.trim()
    }

    /** Строит canonical target key для validator checks. */
    private fun EventAvailabilityOverride.targetKey(): Pair<EventOverrideTargetType, String> {
        return targetType to targetRef.trim()
    }
}
