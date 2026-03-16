package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.StoredHallTemplate
import com.bam.incomedy.server.db.StoredVenue
import com.bam.incomedy.server.db.VenueRepository
import java.util.UUID

/**
 * In-memory реализация `VenueRepository` для route/service тестов organizer venue surface.
 *
 * Репозиторий хранит площадки и hall templates без настоящей БД, чтобы тесты могли проверять
 * create/update/clone сценарии детерминированно.
 */
class InMemoryVenueRepository : VenueRepository {
    /** Площадки по их id. */
    private val venuesById = linkedMapOf<String, MutableVenueRecord>()

    /** Hall templates по их id. */
    private val templatesById = linkedMapOf<String, MutableHallTemplateRecord>()

    override fun listVenues(userId: String): List<StoredVenue> {
        return venuesById.values.map { venue -> venue.toStored(templatesById) }
    }

    override fun createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsJson: String,
    ): StoredVenue {
        val venue = MutableVenueRecord(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            name = name,
            city = city,
            address = address,
            timezone = timezone,
            capacity = capacity,
            description = description,
            contactsJson = contactsJson,
        )
        venuesById[venue.id] = venue
        return venue.toStored(templatesById)
    }

    override fun findVenue(venueId: String): StoredVenue? {
        return venuesById[venueId]?.toStored(templatesById)
    }

    override fun createHallTemplate(
        venueId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate {
        val template = MutableHallTemplateRecord(
            id = UUID.randomUUID().toString(),
            venueId = venueId,
            name = name,
            version = 1,
            status = status,
            layoutJson = layoutJson,
        )
        templatesById[template.id] = template
        venuesById[venueId]?.templateIds?.add(template.id)
        return template.toStored()
    }

    override fun findHallTemplate(templateId: String): StoredHallTemplate? {
        return templatesById[templateId]?.toStored()
    }

    override fun updateHallTemplate(
        templateId: String,
        name: String,
        status: String,
        layoutJson: String,
    ): StoredHallTemplate? {
        val template = templatesById[templateId] ?: return null
        template.name = name
        template.status = status
        template.layoutJson = layoutJson
        template.version += 1
        return template.toStored()
    }

    override fun cloneHallTemplate(
        sourceTemplateId: String,
        name: String,
        status: String,
    ): StoredHallTemplate? {
        val source = templatesById[sourceTemplateId] ?: return null
        return createHallTemplate(
            venueId = source.venueId,
            name = name,
            status = status,
            layoutJson = source.layoutJson,
        )
    }

    /** Mutable in-memory запись площадки. */
    private class MutableVenueRecord(
        val id: String,
        val workspaceId: String,
        var name: String,
        var city: String,
        var address: String,
        var timezone: String,
        var capacity: Int,
        var description: String?,
        var contactsJson: String,
        val templateIds: MutableList<String> = mutableListOf(),
    ) {
        /** Преобразует mutable запись в read-only stored модель. */
        fun toStored(templatesById: Map<String, MutableHallTemplateRecord>): StoredVenue {
            return StoredVenue(
                id = id,
                workspaceId = workspaceId,
                name = name,
                city = city,
                address = address,
                timezone = timezone,
                capacity = capacity,
                description = description,
                contactsJson = contactsJson,
                hallTemplates = templateIds.mapNotNull { templateId -> templatesById[templateId]?.toStored() },
            )
        }
    }

    /** Mutable in-memory запись hall template. */
    private data class MutableHallTemplateRecord(
        val id: String,
        val venueId: String,
        var name: String,
        var version: Int,
        var status: String,
        var layoutJson: String,
    ) {
        /** Преобразует mutable запись в read-only stored модель. */
        fun toStored(): StoredHallTemplate {
            return StoredHallTemplate(
                id = id,
                venueId = venueId,
                name = name,
                version = version,
                status = status,
                layoutJson = layoutJson,
            )
        }
    }
}
