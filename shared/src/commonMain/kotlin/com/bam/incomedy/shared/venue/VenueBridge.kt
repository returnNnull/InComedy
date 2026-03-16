package com.bam.incomedy.shared.venue

import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.feature.venue.HallLayoutEditorCodec
import com.bam.incomedy.feature.venue.VenueFormCodec
import com.bam.incomedy.feature.venue.VenueState
import com.bam.incomedy.feature.venue.VenueViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей venue feature model для Swift-слоя.
 *
 * Bridge скрывает typed Kotlin state/effect потоки и дает SwiftUI стабильные snapshot-модели и
 * высокоуровневые команды create/update/clone без дублирования layout parser logic на iOS.
 *
 * @property viewModel Общая venue feature model.
 */
class VenueBridge(
    private val viewModel: VenueViewModel = InComedyKoin.getVenueViewModel(),
) : BaseFeatureBridge() {

    /** Возвращает текущее состояние venue feature единым снимком. */
    fun currentState(): VenueStateSnapshot = viewModel.state.value.toSnapshot()

    /** Подписывает Swift-слой на обновления venue state. */
    fun observeState(onState: (VenueStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    /** Инициирует загрузку списка площадок. */
    fun loadVenues() {
        viewModel.loadVenues()
    }

    /** Создает площадку из SwiftUI-формы. */
    fun createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsText: String,
    ) {
        val draft = VenueFormCodec.toVenueDraft(
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contactsText = contactsText,
        ).getOrElse { error ->
            viewModel.showLocalError(error.message ?: "Не удалось разобрать контакты площадки")
            return
        }
        viewModel.createVenue(draft)
    }

    /** Создает или обновляет hall template из SwiftUI-редактора. */
    fun saveHallTemplate(
        venueId: String,
        templateId: String?,
        name: String,
        statusKey: String,
        stageLabel: String,
        priceZonesText: String,
        zonesText: String,
        rowsText: String,
        tablesText: String,
        serviceAreasText: String,
        blockedSeatRefsText: String,
    ) {
        val draft = VenueFormCodec.toHallTemplateDraft(
            name = name,
            statusKey = statusKey,
            stageLabel = stageLabel,
            priceZonesText = priceZonesText,
            zonesText = zonesText,
            rowsText = rowsText,
            tablesText = tablesText,
            serviceAreasText = serviceAreasText,
            blockedSeatRefsText = blockedSeatRefsText,
        ).getOrElse { error ->
            viewModel.showLocalError(error.message ?: "Не удалось разобрать шаблон зала")
            return
        }
        if (templateId.isNullOrBlank()) {
            viewModel.createHallTemplate(venueId = venueId, draft = draft)
        } else {
            viewModel.updateHallTemplate(templateId = templateId, draft = draft)
        }
    }

    /** Клонирует hall template из SwiftUI. */
    fun cloneHallTemplate(
        templateId: String,
        clonedName: String?,
    ) {
        viewModel.cloneHallTemplate(
            templateId = templateId,
            clonedName = clonedName?.trim()?.takeIf(String::isNotBlank),
        )
    }

    /** Очищает текущую ошибку venue feature. */
    fun clearError() {
        viewModel.clearError()
    }
}

/** Преобразует внутреннее состояние venue feature в export-friendly snapshot. */
private fun VenueState.toSnapshot(): VenueStateSnapshot {
    return VenueStateSnapshot(
        venues = venues.map(OrganizerVenue::toSnapshot),
        isLoading = isLoading,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
    )
}

/** Преобразует доменную площадку в iOS-friendly snapshot. */
private fun OrganizerVenue.toSnapshot(): VenueSnapshot {
    return VenueSnapshot(
        id = id,
        workspaceId = workspaceId,
        name = name,
        city = city,
        address = address,
        timezone = timezone,
        capacity = capacity,
        description = description,
        contactsText = contacts.joinToString("\n") { contact -> "${contact.label}|${contact.value}" },
        hallTemplates = hallTemplates.map { template ->
            val editor = HallLayoutEditorCodec.fromTemplate(template)
            HallTemplateSnapshot(
                id = template.id,
                venueId = template.venueId,
                name = template.name,
                version = template.version,
                statusKey = template.status.wireName,
                summaryText = HallLayoutEditorCodec.summary(template.layout),
                stageLabel = editor.stageLabel,
                priceZonesText = editor.priceZonesText,
                zonesText = editor.zonesText,
                rowsText = editor.rowsText,
                tablesText = editor.tablesText,
                serviceAreasText = editor.serviceAreasText,
                blockedSeatRefsText = editor.blockedSeatRefsText,
            )
        },
    )
}
