package com.bam.incomedy.server.support

import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.StoredEventAvailabilityOverride
import com.bam.incomedy.server.db.StoredEventHallSnapshot
import com.bam.incomedy.server.db.StoredEventPriceZone
import com.bam.incomedy.server.db.StoredEventPricingAssignment
import com.bam.incomedy.server.db.StoredOrganizerEvent
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация `EventRepository` для route/service тестов organizer event surface.
 *
 * Репозиторий хранит события, frozen snapshots и event-local override-ы без реальной БД, чтобы
 * тесты могли детерминированно проверять create/get/update/lifecycle transition сценарии.
 */
class InMemoryEventRepository : EventRepository {
    /** Mutable события по их id. */
    private val eventsById = linkedMapOf<String, MutableEventRecord>()

    /** Mutable snapshots по их id. */
    private val snapshotsById = linkedMapOf<String, MutableSnapshotRecord>()

    override fun listEvents(userId: String): List<StoredOrganizerEvent> {
        return eventsById.values
            .sortedBy { event -> event.startsAt }
            .mapNotNull { event -> event.toStored(snapshotsById) }
    }

    override fun createEvent(
        workspaceId: String,
        venueId: String,
        venueName: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        status: String,
        salesStatus: String,
        currency: String,
        visibility: String,
        sourceTemplateId: String,
        sourceTemplateName: String,
        snapshotJson: String,
    ): StoredOrganizerEvent {
        val eventId = UUID.randomUUID().toString()
        val snapshotId = UUID.randomUUID().toString()
        val event = MutableEventRecord(
            id = eventId,
            workspaceId = workspaceId,
            venueId = venueId,
            venueName = venueName,
            title = title,
            description = description,
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
            status = status,
            salesStatus = salesStatus,
            currency = currency,
            visibility = visibility,
            hallSnapshotId = snapshotId,
        )
        val snapshot = MutableSnapshotRecord(
            id = snapshotId,
            eventId = eventId,
            sourceTemplateId = sourceTemplateId,
            sourceTemplateName = sourceTemplateName,
            snapshotJson = snapshotJson,
        )
        eventsById[eventId] = event
        snapshotsById[snapshotId] = snapshot
        return event.toStored(snapshotsById) ?: error("Failed to create in-memory event")
    }

    override fun findEvent(eventId: String): StoredOrganizerEvent? {
        return eventsById[eventId]?.toStored(snapshotsById)
    }

    override fun updateEvent(
        eventId: String,
        title: String,
        description: String?,
        startsAt: OffsetDateTime,
        doorsOpenAt: OffsetDateTime?,
        endsAt: OffsetDateTime?,
        currency: String,
        visibility: String,
        priceZones: List<StoredEventPriceZone>,
        pricingAssignments: List<StoredEventPricingAssignment>,
        availabilityOverrides: List<StoredEventAvailabilityOverride>,
    ): StoredOrganizerEvent? {
        val event = eventsById[eventId] ?: return null
        event.title = title
        event.description = description
        event.startsAt = startsAt
        event.doorsOpenAt = doorsOpenAt
        event.endsAt = endsAt
        event.currency = currency
        event.visibility = visibility
        event.priceZones = priceZones.toMutableList()
        event.pricingAssignments = pricingAssignments.toMutableList()
        event.availabilityOverrides = availabilityOverrides.toMutableList()
        return event.toStored(snapshotsById)
    }

    override fun publishEvent(eventId: String): StoredOrganizerEvent? {
        val event = eventsById[eventId] ?: return null
        event.status = "published"
        return event.toStored(snapshotsById)
    }

    override fun openEventSales(eventId: String): StoredOrganizerEvent? {
        val event = eventsById[eventId] ?: return null
        event.salesStatus = "open"
        return event.toStored(snapshotsById)
    }

    override fun pauseEventSales(eventId: String): StoredOrganizerEvent? {
        val event = eventsById[eventId] ?: return null
        event.salesStatus = "paused"
        return event.toStored(snapshotsById)
    }

    override fun cancelEvent(eventId: String): StoredOrganizerEvent? {
        val event = eventsById[eventId] ?: return null
        event.status = "canceled"
        event.salesStatus = "closed"
        return event.toStored(snapshotsById)
    }

    /** Mutable in-memory запись organizer event. */
    private data class MutableEventRecord(
        val id: String,
        val workspaceId: String,
        val venueId: String,
        val venueName: String,
        var title: String,
        var description: String?,
        var startsAt: OffsetDateTime,
        var doorsOpenAt: OffsetDateTime?,
        var endsAt: OffsetDateTime?,
        var status: String,
        var salesStatus: String,
        var currency: String,
        var visibility: String,
        val hallSnapshotId: String,
        var priceZones: MutableList<StoredEventPriceZone> = mutableListOf(),
        var pricingAssignments: MutableList<StoredEventPricingAssignment> = mutableListOf(),
        var availabilityOverrides: MutableList<StoredEventAvailabilityOverride> = mutableListOf(),
    ) {
        /** Преобразует mutable запись в read-only stored модель. */
        fun toStored(
            snapshotsById: Map<String, MutableSnapshotRecord>,
        ): StoredOrganizerEvent? {
            val snapshot = snapshotsById[hallSnapshotId]?.toStored() ?: return null
            return StoredOrganizerEvent(
                id = id,
                workspaceId = workspaceId,
                venueId = venueId,
                venueName = venueName,
                title = title,
                description = description,
                startsAt = startsAt,
                doorsOpenAt = doorsOpenAt,
                endsAt = endsAt,
                status = status,
                salesStatus = salesStatus,
                currency = currency,
                visibility = visibility,
                hallSnapshot = snapshot,
                priceZones = priceZones.toList(),
                pricingAssignments = pricingAssignments.toList(),
                availabilityOverrides = availabilityOverrides.toList(),
            )
        }
    }

    /** Mutable in-memory запись frozen hall snapshot. */
    private data class MutableSnapshotRecord(
        val id: String,
        val eventId: String,
        val sourceTemplateId: String,
        val sourceTemplateName: String,
        val snapshotJson: String,
    ) {
        /** Преобразует mutable snapshot в read-only stored модель. */
        fun toStored(): StoredEventHallSnapshot {
            return StoredEventHallSnapshot(
                id = id,
                eventId = eventId,
                sourceTemplateId = sourceTemplateId,
                sourceTemplateName = sourceTemplateName,
                snapshotJson = snapshotJson,
            )
        }
    }
}
