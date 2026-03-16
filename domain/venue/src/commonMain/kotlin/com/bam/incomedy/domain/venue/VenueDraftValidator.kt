package com.bam.incomedy.domain.venue

/**
 * Набор бизнес-валидаций для venue drafts и hall template layouts.
 *
 * Правила здесь держатся в domain, чтобы Android/iOS, shared layer и backend route validation
 * опирались на один и тот же набор инвариантов builder v1.
 */
object VenueDraftValidator {
    /** Проверяет корректность черновика площадки и возвращает безопасную ошибку для UI/API. */
    fun validateVenueDraft(draft: VenueDraft): String? {
        if (draft.workspaceId.isBlank()) return "Не выбран organizer workspace"
        if (draft.name.trim().length !in 3..80) return "Название площадки должно быть от 3 до 80 символов"
        if (draft.city.trim().length !in 2..80) return "Город площадки должен быть от 2 до 80 символов"
        if (draft.address.trim().length !in 5..200) return "Адрес площадки должен быть от 5 до 200 символов"
        if (draft.capacity !in 1..10_000) return "Вместимость площадки должна быть в диапазоне 1..10000"
        if (draft.description?.trim()?.length ?: 0 > 500) return "Описание площадки не должно превышать 500 символов"
        if (draft.contacts.size > 8) return "Слишком много контактов площадки"
        val invalidContact = draft.contacts.firstOrNull {
            it.label.trim().length !in 2..40 || it.value.trim().length !in 3..120
        }
        if (invalidContact != null) {
            return "Каждый контакт площадки должен содержать подпись и значение разумной длины"
        }
        val timezone = draft.timezone.trim()
        if (timezone.length !in 3..64 || !timezone.contains("/")) {
            return "Некорректная timezone площадки"
        }
        return null
    }

    /** Проверяет корректность hall template draft и его layout-инварианты. */
    fun validateHallTemplateDraft(draft: HallTemplateDraft): String? {
        if (draft.name.trim().length !in 3..80) {
            return "Название шаблона должно быть от 3 до 80 символов"
        }
        return validateHallLayout(draft.layout)
    }

    /** Проверяет каноническую структуру hall layout builder v1. */
    fun validateHallLayout(layout: HallLayout): String? {
        val hasAnyStructure = layout.stage != null ||
            layout.zones.isNotEmpty() ||
            layout.rows.isNotEmpty() ||
            layout.tables.isNotEmpty() ||
            layout.serviceAreas.isNotEmpty()
        if (!hasAnyStructure) {
            return "Схема зала должна содержать хотя бы один структурный элемент"
        }
        if (layout.stage?.label?.trim()?.isEmpty() == true) {
            return "Название сцены не должно быть пустым"
        }
        val duplicatedPriceZoneId = duplicatedId(layout.priceZones.map(HallPriceZone::id))
        if (duplicatedPriceZoneId != null) {
            return "Ценовая зона '$duplicatedPriceZoneId' повторяется"
        }
        val duplicatedZoneId = duplicatedId(layout.zones.map(HallZone::id))
        if (duplicatedZoneId != null) {
            return "Сектор '$duplicatedZoneId' повторяется"
        }
        val duplicatedRowId = duplicatedId(layout.rows.map(HallRow::id))
        if (duplicatedRowId != null) {
            return "Ряд '$duplicatedRowId' повторяется"
        }
        val duplicatedTableId = duplicatedId(layout.tables.map(HallTable::id))
        if (duplicatedTableId != null) {
            return "Стол '$duplicatedTableId' повторяется"
        }
        val duplicatedServiceAreaId = duplicatedId(layout.serviceAreas.map(HallServiceArea::id))
        if (duplicatedServiceAreaId != null) {
            return "Служебная зона '$duplicatedServiceAreaId' повторяется"
        }
        val referencedPriceZoneIds = buildList {
            layout.zones.mapNotNullTo(this) { it.priceZoneId?.takeIf(String::isNotBlank) }
            layout.rows.mapNotNullTo(this) { it.priceZoneId?.takeIf(String::isNotBlank) }
            layout.tables.mapNotNullTo(this) { it.priceZoneId?.takeIf(String::isNotBlank) }
        }
        val unknownPriceZoneId = referencedPriceZoneIds.firstOrNull { referencedId ->
            layout.priceZones.none { it.id == referencedId }
        }
        if (unknownPriceZoneId != null) {
            return "Неизвестная ценовая зона '$unknownPriceZoneId' используется в схеме"
        }
        val seatRefs = buildList {
            layout.rows.forEach { row ->
                if (row.label.trim().isEmpty()) {
                    return "Название ряда '${row.id}' не должно быть пустым"
                }
                if (row.seats.isEmpty()) {
                    return "Ряд '${row.label}' должен содержать хотя бы одно место"
                }
                row.seats.forEach { seat ->
                    if (seat.ref.trim().isEmpty() || seat.label.trim().isEmpty()) {
                        return "Каждое место должно иметь ref и label"
                    }
                    add(seat.ref)
                }
            }
        }
        val duplicatedSeatRef = duplicatedId(seatRefs)
        if (duplicatedSeatRef != null) {
            return "Место '$duplicatedSeatRef' повторяется в схеме"
        }
        val unknownBlockedSeatRef = layout.blockedSeatRefs.firstOrNull { blockedRef ->
            blockedRef !in seatRefs
        }
        if (unknownBlockedSeatRef != null) {
            return "Заблокированное место '$unknownBlockedSeatRef' отсутствует в рядах"
        }
        val invalidZone = layout.zones.firstOrNull {
            it.name.trim().isEmpty() || it.capacity <= 0 || it.kind.trim().isEmpty()
        }
        if (invalidZone != null) {
            return "Каждый сектор должен иметь имя, тип и положительную вместимость"
        }
        val invalidTable = layout.tables.firstOrNull {
            it.label.trim().isEmpty() || it.seatCount <= 0
        }
        if (invalidTable != null) {
            return "Каждый стол должен иметь название и положительное число мест"
        }
        val invalidServiceArea = layout.serviceAreas.firstOrNull {
            it.name.trim().isEmpty() || it.kind.trim().isEmpty()
        }
        if (invalidServiceArea != null) {
            return "Каждая служебная зона должна иметь имя и тип"
        }
        return null
    }

    /** Возвращает первый повторяющийся идентификатор из набора. */
    private fun duplicatedId(ids: List<String>): String? {
        val seen = linkedSetOf<String>()
        return ids.firstOrNull { !seen.add(it) }
    }
}
