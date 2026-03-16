package com.bam.incomedy.feature.venue.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.feature.venue.VenueFormCodec
import com.bam.incomedy.feature.venue.VenueState
import com.bam.incomedy.feature.venue.VenueViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер organizer venue feature, который удерживает общую KMP-модель и принимает
 * platform-friendly form values от Compose UI.
 *
 * @property application Android application context.
 */
class VenueAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая venue feature model из KMP-слоя. */
    private val sharedViewModel: VenueViewModel = InComedyKoin.getVenueViewModel()

    /** Состояние organizer venue feature для Compose UI. */
    val state: StateFlow<VenueState> = sharedViewModel.state

    init {
        sharedViewModel.loadVenues()
    }

    /** Явно перезагружает список площадок текущей organizer-сессии. */
    fun refreshVenues() {
        sharedViewModel.loadVenues()
    }

    /**
     * Создает новую площадку из текстовой Compose-формы.
     *
     * @param workspaceId Идентификатор выбранного workspace.
     * @param name Название площадки.
     * @param city Город площадки.
     * @param address Адрес площадки.
     * @param timezone IANA timezone площадки.
     * @param capacityText Вместимость площадки в текстовом виде.
     * @param description Необязательное описание площадки.
     * @param contactsText Контакты формата `label|value`.
     */
    fun createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacityText: String,
        description: String?,
        contactsText: String,
    ) {
        val capacity = capacityText.trim().toIntOrNull()
        if (capacity == null) {
            sharedViewModel.showLocalError("Вместимость площадки должна быть целым числом")
            return
        }
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
            sharedViewModel.showLocalError(error.message ?: "Не удалось разобрать форму площадки")
            return
        }
        sharedViewModel.createVenue(draft)
    }

    /**
     * Создает или обновляет hall template из текстового builder input-а.
     *
     * @param venueId Идентификатор площадки-владельца.
     * @param templateId Необязательный id шаблона; при `null` создается новый шаблон.
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
            sharedViewModel.showLocalError(error.message ?: "Не удалось разобрать шаблон зала")
            return
        }
        if (templateId.isNullOrBlank()) {
            sharedViewModel.createHallTemplate(venueId = venueId, draft = draft)
        } else {
            sharedViewModel.updateHallTemplate(templateId = templateId, draft = draft)
        }
    }

    /**
     * Клонирует существующий hall template.
     *
     * @param templateId Идентификатор исходного шаблона.
     * @param clonedName Необязательное новое имя шаблона.
     */
    fun cloneHallTemplate(
        templateId: String,
        clonedName: String?,
    ) {
        sharedViewModel.cloneHallTemplate(
            templateId = templateId,
            clonedName = clonedName?.trim()?.takeIf(String::isNotBlank),
        )
    }

    /** Очищает текущую venue-specific ошибку. */
    fun clearError() {
        sharedViewModel.clearError()
    }
}
