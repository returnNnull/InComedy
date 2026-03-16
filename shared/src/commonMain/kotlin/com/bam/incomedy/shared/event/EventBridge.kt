package com.bam.incomedy.shared.event

import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.feature.event.EventFormCodec
import com.bam.incomedy.feature.event.EventOverrideEditorCodec
import com.bam.incomedy.feature.event.EventState
import com.bam.incomedy.feature.event.EventViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей event feature model для Swift-слоя.
 *
 * Bridge скрывает typed Kotlin state/effect потоки и дает SwiftUI стабильные snapshot-модели и
 * высокоуровневые команды create/update/publish без дублирования form normalization logic на iOS.
 *
 * @property viewModel Общая event feature model.
 */
class EventBridge(
    private val viewModel: EventViewModel = InComedyKoin.getEventViewModel(),
) : BaseFeatureBridge() {
    /** Возвращает текущее состояние event feature единым снимком. */
    fun currentState(): EventStateSnapshot = viewModel.state.value.toSnapshot()

    /** Подписывает Swift-слой на обновления event state. */
    fun observeState(onState: (EventStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    /** Инициирует загрузку event context. */
    fun loadContext() {
        viewModel.loadContext()
    }

    /** Создает organizer event draft из SwiftUI-формы. */
    fun createEvent(
        workspaceId: String,
        venueId: String,
        hallTemplateId: String,
        title: String,
        description: String?,
        startsAtIso: String,
        doorsOpenAtIso: String?,
        endsAtIso: String?,
        currency: String,
        visibilityKey: String,
    ) {
        val draft = EventFormCodec.toEventDraft(
            workspaceId = workspaceId,
            venueId = venueId,
            hallTemplateId = hallTemplateId,
            title = title,
            description = description,
            startsAtIso = startsAtIso,
            doorsOpenAtIso = doorsOpenAtIso,
            endsAtIso = endsAtIso,
            currency = currency,
            visibilityKey = visibilityKey,
        ).getOrElse { error ->
            viewModel.showLocalError(error.message ?: "Не удалось разобрать форму события")
            return
        }
        viewModel.createEvent(draft)
    }

    /** Обновляет organizer event details и event-local overrides из SwiftUI-редактора. */
    fun updateEvent(
        eventId: String,
        title: String,
        description: String?,
        startsAtIso: String,
        doorsOpenAtIso: String?,
        endsAtIso: String?,
        currency: String,
        visibilityKey: String,
        priceZonesText: String,
        pricingAssignmentsText: String,
        availabilityOverridesText: String,
    ) {
        val event = viewModel.state.value.events.firstOrNull { it.id == eventId }
        if (event == null) {
            viewModel.showLocalError("Событие для редактирования не найдено")
            return
        }
        val draft = EventFormCodec.toEventUpdateDraft(
            event = event,
            title = title,
            description = description,
            startsAtIso = startsAtIso,
            doorsOpenAtIso = doorsOpenAtIso,
            endsAtIso = endsAtIso,
            currency = currency,
            visibilityKey = visibilityKey,
            priceZonesText = priceZonesText,
            pricingAssignmentsText = pricingAssignmentsText,
            availabilityOverridesText = availabilityOverridesText,
        ).getOrElse { error ->
            viewModel.showLocalError(error.message ?: "Не удалось разобрать override-ы события")
            return
        }
        viewModel.updateEvent(eventId = eventId, draft = draft)
    }

    /** Публикует draft-событие из SwiftUI. */
    fun publishEvent(eventId: String) {
        viewModel.publishEvent(eventId)
    }

    /** Открывает продажи опубликованного события из SwiftUI. */
    fun openEventSales(eventId: String) {
        viewModel.openEventSales(eventId)
    }

    /** Ставит продажи события на паузу из SwiftUI. */
    fun pauseEventSales(eventId: String) {
        viewModel.pauseEventSales(eventId)
    }

    /** Отменяет событие из SwiftUI. */
    fun cancelEvent(eventId: String) {
        viewModel.cancelEvent(eventId)
    }

    /** Очищает текущую ошибку event feature. */
    fun clearError() {
        viewModel.clearError()
    }
}

/** Преобразует внутреннее состояние event feature в export-friendly snapshot. */
private fun EventState.toSnapshot(): EventStateSnapshot {
    return EventStateSnapshot(
        events = events.map(OrganizerEvent::toSnapshot),
        venues = venues.map(OrganizerVenue::toEventOptionSnapshot),
        isLoading = isLoading,
        isSubmitting = isSubmitting,
        errorMessage = errorMessage,
    )
}

/** Преобразует доменное событие в iOS-friendly snapshot. */
private fun OrganizerEvent.toSnapshot(): EventSnapshot {
    val editorInput = EventOverrideEditorCodec.fromEvent(this)
    return EventSnapshot(
        id = id,
        workspaceId = workspaceId,
        venueId = venueId,
        venueName = venueName,
        hallSnapshotId = hallSnapshotId,
        sourceTemplateId = sourceTemplateId,
        sourceTemplateName = sourceTemplateName,
        title = title,
        description = description,
        startsAtIso = startsAtIso,
        doorsOpenAtIso = doorsOpenAtIso,
        endsAtIso = endsAtIso,
        statusKey = status.wireName,
        salesStatusKey = salesStatus.wireName,
        currency = currency,
        visibilityKey = visibility.wireName,
        layoutRowCount = hallSnapshot.layout.rows.size,
        layoutZoneCount = hallSnapshot.layout.zones.size,
        layoutTableCount = hallSnapshot.layout.tables.size,
        overrideSummaryText = EventOverrideEditorCodec.summary(this),
        targetHintText = EventOverrideEditorCodec.targetHint(this),
        priceZonesText = editorInput.priceZonesText,
        pricingAssignmentsText = editorInput.pricingAssignmentsText,
        availabilityOverridesText = editorInput.availabilityOverridesText,
    )
}

/** Преобразует площадку в iOS-friendly option snapshot для event form. */
private fun OrganizerVenue.toEventOptionSnapshot(): EventVenueOptionSnapshot {
    return EventVenueOptionSnapshot(
        id = id,
        workspaceId = workspaceId,
        name = name,
        hallTemplates = hallTemplates.map(HallTemplate::toEventOptionSnapshot),
    )
}

/** Преобразует hall template в iOS-friendly option snapshot. */
private fun HallTemplate.toEventOptionSnapshot(): EventTemplateOptionSnapshot {
    return EventTemplateOptionSnapshot(
        id = id,
        name = name,
        version = version,
        statusKey = status.wireName,
    )
}
