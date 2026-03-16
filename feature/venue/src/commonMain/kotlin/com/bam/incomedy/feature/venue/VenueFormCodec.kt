package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.VenueContact
import com.bam.incomedy.domain.venue.VenueDraft

/**
 * Общий codec organizer form payload-ов для venue bounded context-а.
 *
 * Codec централизует разбор текстовых multiline полей и не дает Android/iOS расходиться в
 * форматах `contactsText` и hall layout editor input-а.
 */
object VenueFormCodec {
    /**
     * Собирает `VenueDraft` из platform form values.
     *
     * @param workspaceId Идентификатор выбранного organizer workspace.
     * @param name Название площадки.
     * @param city Город площадки.
     * @param address Адрес площадки.
     * @param timezone IANA timezone площадки.
     * @param capacity Вместимость площадки.
     * @param description Необязательное описание площадки.
     * @param contactsText Контакты в формате `label|value` по одной строке на запись.
     */
    fun toVenueDraft(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsText: String,
    ): Result<VenueDraft> {
        return runCatching {
            VenueDraft(
                workspaceId = workspaceId.trim(),
                name = name.trim(),
                city = city.trim(),
                address = address.trim(),
                timezone = timezone.trim(),
                capacity = capacity,
                description = description?.trim()?.takeIf(String::isNotBlank),
                contacts = parseContacts(contactsText),
            )
        }
    }

    /**
     * Собирает `HallTemplateDraft` из текстового builder input-а.
     *
     * @param name Название шаблона.
     * @param statusKey Wire-ключ статуса шаблона.
     * @param stageLabel Название сцены.
     * @param priceZonesText Текст ценовых зон.
     * @param zonesText Текст standing/sector зон.
     * @param rowsText Текст рядов.
     * @param tablesText Текст столов.
     * @param serviceAreasText Текст служебных зон.
     * @param blockedSeatRefsText Список blocked seats.
     */
    fun toHallTemplateDraft(
        name: String,
        statusKey: String,
        stageLabel: String,
        priceZonesText: String,
        zonesText: String,
        rowsText: String,
        tablesText: String,
        serviceAreasText: String,
        blockedSeatRefsText: String,
    ): Result<HallTemplateDraft> {
        val status = HallTemplateStatus.fromWireName(statusKey.trim()) ?: HallTemplateStatus.DRAFT
        return HallLayoutEditorCodec.toDraft(
            name = name,
            status = status,
            input = HallLayoutEditorInput(
                stageLabel = stageLabel,
                priceZonesText = priceZonesText,
                zonesText = zonesText,
                rowsText = rowsText,
                tablesText = tablesText,
                serviceAreasText = serviceAreasText,
                blockedSeatRefsText = blockedSeatRefsText,
            ),
        )
    }

    /** Разбирает контакты площадки из текстового представления `label|value`. */
    private fun parseContacts(raw: String): List<VenueContact> {
        return raw.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split("|").map(String::trim)
                require(parts.size == 2) { "Контакт площадки должен иметь формат label|value" }
                VenueContact(
                    label = parts[0],
                    value = parts[1],
                )
            }
    }
}
