package com.bam.incomedy.shared.event

/**
 * Export-friendly состояние organizer event feature для Swift-слоя.
 *
 * @property events Список organizer events.
 * @property venues Доступные площадки и их templates для event form.
 * @property isLoading Показывает загрузку event context.
 * @property isSubmitting Показывает активную мутацию create/publish.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class EventStateSnapshot(
    val events: List<EventSnapshot>,
    val venues: List<EventVenueOptionSnapshot>,
    val isLoading: Boolean,
    val isSubmitting: Boolean,
    val errorMessage: String?,
)

/**
 * Export-friendly organizer event.
 */
data class EventSnapshot(
    val id: String,
    val workspaceId: String,
    val venueId: String,
    val venueName: String,
    val hallSnapshotId: String,
    val sourceTemplateId: String,
    val sourceTemplateName: String,
    val title: String,
    val description: String?,
    val startsAtIso: String,
    val doorsOpenAtIso: String?,
    val endsAtIso: String?,
    val statusKey: String,
    val salesStatusKey: String,
    val currency: String,
    val visibilityKey: String,
    val layoutRowCount: Int,
    val layoutZoneCount: Int,
    val layoutTableCount: Int,
)

/**
 * Export-friendly площадка для event form.
 */
data class EventVenueOptionSnapshot(
    val id: String,
    val workspaceId: String,
    val name: String,
    val hallTemplates: List<EventTemplateOptionSnapshot>,
)

/**
 * Export-friendly hall template option для event form.
 */
data class EventTemplateOptionSnapshot(
    val id: String,
    val name: String,
    val version: Int,
    val statusKey: String,
)
