package com.bam.incomedy.feature.event

import com.bam.incomedy.domain.event.EventDraft
import com.bam.incomedy.domain.event.EventHallSnapshot
import com.bam.incomedy.domain.event.EventManagementService
import com.bam.incomedy.domain.event.EventOverrideTargetType
import com.bam.incomedy.domain.event.EventPriceZone
import com.bam.incomedy.domain.event.EventPricingAssignment
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventUpdateDraft
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.domain.venue.VenueManagementService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit-тесты shared event feature model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {
    /** Проверяет загрузку event context из services. */
    @Test
    fun loadContextStoresEventsAndVenues() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = EventViewModel(
            eventManagementService = FakeEventManagementService(),
            venueManagementService = FakeVenueManagementService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadContext()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.events.size)
        assertEquals(1, viewModel.state.value.venues.size)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    /** Проверяет локальную validation ошибку до network roundtrip. */
    @Test
    fun createEventShowsValidationErrorForBlankTemplate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = EventViewModel(
            eventManagementService = FakeEventManagementService(),
            venueManagementService = FakeVenueManagementService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.createEvent(
            EventDraft(
                workspaceId = "ws-1",
                venueId = "venue-1",
                hallTemplateId = "",
                title = "Late Night Standup",
                startsAtIso = "2026-03-20T19:00:00+03:00",
            ),
        )
        advanceUntilIdle()

        assertEquals("Не выбран шаблон зала", viewModel.state.value.errorMessage)
    }

    /** Проверяет публикацию draft-события с обновлением списка. */
    @Test
    fun publishEventRefreshesList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventService = FakeEventManagementService()
        val viewModel = EventViewModel(
            eventManagementService = eventService,
            venueManagementService = FakeVenueManagementService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.publishEvent("event-1")
        advanceUntilIdle()

        assertEquals(EventStatus.PUBLISHED, eventService.events.single().status)
        assertEquals(EventStatus.PUBLISHED, viewModel.state.value.events.single().status)
    }

    /** Проверяет обновление organizer event details и event-local price assignments. */
    @Test
    fun updateEventRefreshesList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val eventService = FakeEventManagementService()
        val viewModel = EventViewModel(
            eventManagementService = eventService,
            venueManagementService = FakeVenueManagementService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.updateEvent(
            eventId = "event-1",
            draft = EventUpdateDraft(
                title = "Updated Event",
                startsAtIso = "2026-03-20T19:00:00+03:00",
                currency = "RUB",
                visibility = EventVisibility.PUBLIC,
                priceZones = listOf(
                    EventPriceZone(
                        id = "event-vip",
                        name = "VIP",
                        priceMinor = 3_500,
                        currency = "RUB",
                    ),
                ),
                pricingAssignments = listOf(
                    EventPricingAssignment(
                        targetType = EventOverrideTargetType.ROW,
                        targetRef = "row-a",
                        eventPriceZoneId = "event-vip",
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("Updated Event", eventService.events.single().title)
        assertEquals(1, viewModel.state.value.events.single().priceZones.size)
        assertEquals("event-vip", viewModel.state.value.events.single().priceZones.single().id)
    }
}

/**
 * Тестовый event service для shared ViewModel.
 */
private class FakeEventManagementService : EventManagementService {
    val events = mutableListOf(
        OrganizerEvent(
            id = "event-1",
            workspaceId = "ws-1",
            venueId = "venue-1",
            venueName = "Moscow Cellar",
            hallSnapshotId = "snapshot-1",
            sourceTemplateId = "template-1",
            sourceTemplateName = "Late Layout",
            title = "Late Night Standup",
            description = "Вечер проверки event foundation",
            startsAtIso = "2026-03-20T19:00:00+03:00",
            doorsOpenAtIso = "2026-03-20T18:30:00+03:00",
            endsAtIso = "2026-03-20T21:00:00+03:00",
            status = EventStatus.DRAFT,
            salesStatus = EventSalesStatus.CLOSED,
            currency = "RUB",
            visibility = EventVisibility.PUBLIC,
            hallSnapshot = EventHallSnapshot(
                id = "snapshot-1",
                eventId = "event-1",
                sourceTemplateId = "template-1",
                layout = HallLayout(),
            ),
        ),
    )

    override suspend fun listEvents(accessToken: String): Result<List<OrganizerEvent>> = Result.success(events.toList())

    override suspend fun getEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent> {
        return Result.success(events.first { it.id == eventId })
    }

    override suspend fun createEvent(
        accessToken: String,
        draft: EventDraft,
    ): Result<OrganizerEvent> {
        val created = events.first().copy(
            id = "event-created",
            title = draft.title,
            workspaceId = draft.workspaceId,
            venueId = draft.venueId,
            sourceTemplateId = draft.hallTemplateId,
            startsAtIso = draft.startsAtIso,
        )
        events.add(created)
        return Result.success(created)
    }

    override suspend fun publishEvent(
        accessToken: String,
        eventId: String,
    ): Result<OrganizerEvent> {
        val updated = events.first { it.id == eventId }.copy(status = EventStatus.PUBLISHED)
        val index = events.indexOfFirst { it.id == eventId }
        if (index >= 0) {
            events[index] = updated
        }
        return Result.success(updated)
    }

    override suspend fun updateEvent(
        accessToken: String,
        eventId: String,
        draft: EventUpdateDraft,
    ): Result<OrganizerEvent> {
        val updated = events.first { it.id == eventId }.copy(
            title = draft.title,
            description = draft.description,
            startsAtIso = draft.startsAtIso,
            doorsOpenAtIso = draft.doorsOpenAtIso,
            endsAtIso = draft.endsAtIso,
            currency = draft.currency,
            visibility = draft.visibility,
            priceZones = draft.priceZones,
            pricingAssignments = draft.pricingAssignments,
            availabilityOverrides = draft.availabilityOverrides,
        )
        val index = events.indexOfFirst { it.id == eventId }
        if (index >= 0) {
            events[index] = updated
        }
        return Result.success(updated)
    }
}

/**
 * Тестовый venue service для shared ViewModel.
 */
private class FakeVenueManagementService : VenueManagementService {
    override suspend fun listVenues(accessToken: String): Result<List<OrganizerVenue>> {
        return Result.success(
            listOf(
                OrganizerVenue(
                    id = "venue-1",
                    workspaceId = "ws-1",
                    name = "Moscow Cellar",
                    city = "Moscow",
                    address = "Tverskaya 1",
                    timezone = "Europe/Moscow",
                    capacity = 120,
                    hallTemplates = listOf(
                        HallTemplate(
                            id = "template-1",
                            venueId = "venue-1",
                            name = "Late Layout",
                            version = 1,
                            status = HallTemplateStatus.PUBLISHED,
                            layout = HallLayout(),
                        ),
                    ),
                ),
            ),
        )
    }

    override suspend fun createVenue(accessToken: String, draft: com.bam.incomedy.domain.venue.VenueDraft) =
        error("Not used in event tests")

    override suspend fun createHallTemplate(
        accessToken: String,
        venueId: String,
        draft: com.bam.incomedy.domain.venue.HallTemplateDraft,
    ) = error("Not used in event tests")

    override suspend fun updateHallTemplate(
        accessToken: String,
        templateId: String,
        draft: com.bam.incomedy.domain.venue.HallTemplateDraft,
    ) = error("Not used in event tests")

    override suspend fun cloneHallTemplate(
        accessToken: String,
        templateId: String,
        clonedName: String?,
    ) = error("Not used in event tests")
}
