package com.bam.incomedy.server.db

/**
 * Сохраненная площадка organizer bounded context-а.
 *
 * @property id Идентификатор площадки.
 * @property workspaceId Workspace-владелец площадки.
 * @property name Название площадки.
 * @property city Город площадки.
 * @property address Адрес площадки.
 * @property timezone IANA timezone площадки.
 * @property capacity Вместимость площадки.
 * @property description Краткое описание площадки.
 * @property contactsJson JSON-представление contacts list.
 * @property hallTemplates Вложенные hall templates площадки.
 */
data class StoredVenue(
    val id: String,
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contactsJson: String = "[]",
    val hallTemplates: List<StoredHallTemplate> = emptyList(),
)

/**
 * Сохраненный hall template площадки.
 *
 * @property id Идентификатор шаблона.
 * @property venueId Идентификатор площадки-владельца.
 * @property name Название шаблона.
 * @property version Версия шаблона.
 * @property status Lifecycle-статус шаблона.
 * @property layoutJson JSON-представление typed hall layout.
 */
data class StoredHallTemplate(
    val id: String,
    val venueId: String,
    val name: String,
    val version: Int,
    val status: String,
    val layoutJson: String,
)

/**
 * Persistence-контракт venue management bounded context-а.
 */
interface VenueRepository {
    /** Возвращает список площадок и вложенных шаблонов, доступных пользователю. */
    fun listVenues(userId: String): List<StoredVenue>

    /** Создает новую площадку внутри workspace. */
    fun createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsJson: String,
    ): StoredVenue

    /** Возвращает площадку по ее id вместе со всеми шаблонами. */
    fun findVenue(venueId: String): StoredVenue?

    /** Создает новый hall template внутри площадки. */
    fun createHallTemplate(
        venueId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate

    /** Возвращает hall template по id. */
    fun findHallTemplate(templateId: String): StoredHallTemplate?

    /** Обновляет hall template с инкрементом версии. */
    fun updateHallTemplate(
        templateId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate?

    /** Клонирует hall template в ту же площадку с новым именем и статусом. */
    fun cloneHallTemplate(
        sourceTemplateId: String,
        name: String,
        status: String,
    ): StoredHallTemplate?
}
