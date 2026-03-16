package com.bam.incomedy.feature.venue

import com.bam.incomedy.domain.venue.HallLayout
import com.bam.incomedy.domain.venue.HallStage
import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.domain.venue.VenueDraft
import com.bam.incomedy.domain.venue.VenueManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VenueViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load venues updates state from service`() = runTest(dispatcher) {
        val service = FakeVenueManagementService(
            venues = mutableListOf(
                OrganizerVenue(
                    id = "venue-1",
                    workspaceId = "ws-1",
                    name = "Moscow Cellar",
                    city = "Moscow",
                    address = "Tverskaya 1",
                    timezone = "Europe/Moscow",
                    capacity = 120,
                ),
            ),
        )
        val viewModel = VenueViewModel(
            venueManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadVenues()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.venues.size)
        assertEquals("Moscow Cellar", viewModel.state.value.venues.single().name)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `create venue validates draft before mutation`() = runTest(dispatcher) {
        val service = FakeVenueManagementService()
        val viewModel = VenueViewModel(
            venueManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.createVenue(
            VenueDraft(
                workspaceId = "",
                name = "A",
                city = "",
                address = "",
                timezone = "Europe/Moscow",
                capacity = 0,
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.errorMessage?.isNotBlank() == true)
        assertEquals(0, service.createdVenueDrafts.size)
    }

    @Test
    fun `create template emits success effect and refreshes venues`() = runTest(dispatcher) {
        val service = FakeVenueManagementService()
        val viewModel = VenueViewModel(
            venueManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )
        val effectDeferred = async { viewModel.effects.first() }

        viewModel.createHallTemplate(
            venueId = "venue-1",
            draft = HallTemplateDraft(
                name = "Late Layout",
                layout = HallLayout(stage = HallStage("Stage")),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, service.createdTemplateDrafts.size)
        assertEquals("Шаблон зала создан", (effectDeferred.await() as VenueEffect.MutationCompleted).message)
    }

    private class FakeVenueManagementService(
        venues: MutableList<OrganizerVenue> = mutableListOf(),
    ) : VenueManagementService {
        val storedVenues = venues
        val createdVenueDrafts = mutableListOf<VenueDraft>()
        val createdTemplateDrafts = mutableListOf<HallTemplateDraft>()

        override suspend fun listVenues(accessToken: String): Result<List<OrganizerVenue>> {
            return Result.success(storedVenues.toList())
        }

        override suspend fun createVenue(
            accessToken: String,
            draft: VenueDraft,
        ): Result<OrganizerVenue> {
            createdVenueDrafts += draft
            val venue = OrganizerVenue(
                id = "venue-${createdVenueDrafts.size}",
                workspaceId = draft.workspaceId,
                name = draft.name,
                city = draft.city,
                address = draft.address,
                timezone = draft.timezone,
                capacity = draft.capacity,
                description = draft.description,
                contacts = draft.contacts,
            )
            storedVenues += venue
            return Result.success(venue)
        }

        override suspend fun createHallTemplate(
            accessToken: String,
            venueId: String,
            draft: HallTemplateDraft,
        ): Result<com.bam.incomedy.domain.venue.HallTemplate> {
            createdTemplateDrafts += draft
            return Result.success(
                com.bam.incomedy.domain.venue.HallTemplate(
                    id = "template-${createdTemplateDrafts.size}",
                    venueId = venueId,
                    name = draft.name,
                    version = 1,
                    status = draft.status,
                    layout = draft.layout,
                ),
            )
        }

        override suspend fun updateHallTemplate(
            accessToken: String,
            templateId: String,
            draft: HallTemplateDraft,
        ): Result<com.bam.incomedy.domain.venue.HallTemplate> {
            return createHallTemplate(accessToken, "venue-1", draft)
        }

        override suspend fun cloneHallTemplate(
            accessToken: String,
            templateId: String,
            clonedName: String?,
        ): Result<com.bam.incomedy.domain.venue.HallTemplate> {
            return Result.success(
                com.bam.incomedy.domain.venue.HallTemplate(
                    id = "clone-1",
                    venueId = "venue-1",
                    name = clonedName ?: "Clone",
                    version = 1,
                    status = com.bam.incomedy.domain.venue.HallTemplateStatus.DRAFT,
                    layout = HallLayout(stage = HallStage("Stage")),
                ),
            )
        }
    }
}
