package com.bam.incomedy.shared.venue

/**
 * Экспортируемый snapshot состояния venue feature для Swift-слоя.
 *
 * @property venues Площадки и шаблоны, доступные текущей сессии.
 * @property isLoading Показывает активную загрузку списка площадок.
 * @property isSubmitting Показывает активную create/update/clone мутацию.
 * @property errorMessage Безопасная ошибка верхнего уровня.
 */
data class VenueStateSnapshot(
    val venues: List<VenueSnapshot>,
    val isLoading: Boolean,
    val isSubmitting: Boolean,
    val errorMessage: String?,
)

/**
 * Snapshot площадки для iOS UI.
 *
 * @property id Идентификатор площадки.
 * @property workspaceId Workspace-владелец площадки.
 * @property name Название площадки.
 * @property city Город площадки.
 * @property address Адрес площадки.
 * @property timezone IANA timezone площадки.
 * @property capacity Вместимость площадки.
 * @property description Описание площадки.
 * @property contactsText Контакты площадки в текстовом виде для SwiftUI-форм.
 * @property hallTemplates Список шаблонов, принадлежащих площадке.
 */
data class VenueSnapshot(
    val id: String,
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String?,
    val contactsText: String,
    val hallTemplates: List<HallTemplateSnapshot>,
)

/**
 * Snapshot hall template для iOS UI.
 *
 * @property id Идентификатор шаблона.
 * @property venueId Идентификатор площадки-владельца.
 * @property name Название шаблона.
 * @property version Текущая версия шаблона.
 * @property statusKey Wire-ключ lifecycle-статуса.
 * @property summaryText Краткая summary layout-а для карточки.
 * @property stageLabel Название сцены.
 * @property priceZonesText Текстовый editor input ценовых зон.
 * @property zonesText Текстовый editor input standing/sector зон.
 * @property rowsText Текстовый editor input рядов.
 * @property tablesText Текстовый editor input столов.
 * @property serviceAreasText Текстовый editor input служебных зон.
 * @property blockedSeatRefsText Текстовый editor input blocked seats.
 */
data class HallTemplateSnapshot(
    val id: String,
    val venueId: String,
    val name: String,
    val version: Int,
    val statusKey: String,
    val summaryText: String,
    val stageLabel: String,
    val priceZonesText: String,
    val zonesText: String,
    val rowsText: String,
    val tablesText: String,
    val serviceAreasText: String,
    val blockedSeatRefsText: String,
)
