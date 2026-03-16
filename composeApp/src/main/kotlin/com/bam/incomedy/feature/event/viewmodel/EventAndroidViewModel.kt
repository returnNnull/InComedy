package com.bam.incomedy.feature.event.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.feature.event.EventFormCodec
import com.bam.incomedy.feature.event.EventState
import com.bam.incomedy.feature.event.EventViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер organizer event feature, который удерживает общую KMP-модель и принимает
 * platform-friendly form values от Compose UI.
 *
 * @property application Android application context.
 */
class EventAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая event feature model из KMP-слоя. */
    private val sharedViewModel: EventViewModel = InComedyKoin.getEventViewModel()

    /** Состояние organizer event feature для Compose UI. */
    val state: StateFlow<EventState> = sharedViewModel.state

    init {
        sharedViewModel.loadContext()
    }

    /** Явно перезагружает organizer event context вместе с reference venues. */
    fun refreshContext() {
        sharedViewModel.loadContext()
    }

    /**
     * Создает organizer event draft из текстовой Compose-формы.
     *
     * @param workspaceId Идентификатор выбранного organizer workspace.
     * @param venueId Идентификатор выбранной площадки.
     * @param hallTemplateId Идентификатор выбранного hall template.
     * @param title Название события.
     * @param description Необязательное описание события.
     * @param startsAtIso ISO timestamp начала события.
     * @param doorsOpenAtIso ISO timestamp открытия дверей.
     * @param endsAtIso ISO timestamp окончания события.
     * @param currency Валюта события.
     * @param visibilityKey Wire-ключ публичности события.
     */
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
            sharedViewModel.showLocalError(error.message ?: "Не удалось разобрать форму события")
            return
        }
        sharedViewModel.createEvent(draft)
    }

    /**
     * Публикует существующий organizer event draft.
     *
     * @param eventId Идентификатор события.
     */
    fun publishEvent(eventId: String) {
        sharedViewModel.publishEvent(eventId = eventId)
    }

    /** Очищает текущую event-specific ошибку. */
    fun clearError() {
        sharedViewModel.clearError()
    }
}
