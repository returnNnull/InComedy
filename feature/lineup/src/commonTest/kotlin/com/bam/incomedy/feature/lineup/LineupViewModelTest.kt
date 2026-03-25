package com.bam.incomedy.feature.lineup

import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.domain.lineup.LineupEntryOrderUpdate
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.domain.lineup.LineupLiveEntry
import com.bam.incomedy.domain.lineup.LineupLiveSummary
import com.bam.incomedy.domain.lineup.LineupLiveUpdate
import com.bam.incomedy.domain.lineup.LineupLiveUpdateType
import com.bam.incomedy.domain.lineup.LineupManagementService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit-тесты shared lineup feature model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LineupViewModelTest {
    /** Проверяет organizer context load для applications и lineup. */
    @Test
    fun loadOrganizerContextStoresApplicationsAndLineup() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService()
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()

        assertEquals("event-1", viewModel.state.value.selectedEventId)
        assertEquals(1, viewModel.state.value.applications.size)
        assertEquals(1, viewModel.state.value.lineup.size)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    /** Проверяет, что organizer context корректно вычисляет current performer и next up. */
    @Test
    fun loadOrganizerContextExposesLiveStagePointers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(
                defaultLineupEntry(id = "entry-up-next", orderIndex = 1, status = LineupEntryStatus.UP_NEXT),
                defaultLineupEntry(id = "entry-on-stage", orderIndex = 2, status = LineupEntryStatus.ON_STAGE),
            ),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()

        assertEquals("entry-on-stage", viewModel.state.value.currentPerformer?.id)
        assertEquals("entry-up-next", viewModel.state.value.nextUpPerformer?.id)
    }

    /** Проверяет, что comedian submit локально добавляет новую заявку в state. */
    @Test
    fun submitApplicationAppendsApplicationToState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService()
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()
        viewModel.submitApplication(
            eventId = "event-1",
            note = "Новый пятиминутный сет",
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.applications.size)
        assertEquals("submitted", viewModel.state.value.applications.first().status.wireName)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    /** Проверяет, что approve refresh-ит organizer context и материализует lineup entry. */
    @Test
    fun updateApplicationStatusReloadsOrganizerContext() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService()
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.updateApplicationStatus(
            eventId = "event-1",
            applicationId = "application-1",
            status = ComedianApplicationStatus.APPROVED,
        )
        advanceUntilIdle()

        assertEquals(ComedianApplicationStatus.APPROVED, viewModel.state.value.applications.single().status)
        assertEquals(2, viewModel.state.value.lineup.size)
        assertEquals("application-1", viewModel.state.value.lineup.last().applicationId)
    }

    /** Проверяет, что reorder lineup обновляет explicit порядок в state. */
    @Test
    fun reorderLineupUpdatesLocalOrdering() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(
                defaultLineupEntry(id = "entry-1", orderIndex = 1),
                defaultLineupEntry(id = "entry-2", orderIndex = 2),
            ),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.reorderLineup(
            eventId = "event-1",
            orderedEntryIds = listOf("entry-2", "entry-1"),
        )
        advanceUntilIdle()

        assertEquals(listOf("entry-2", "entry-1"), viewModel.state.value.lineup.map(LineupEntry::id))
        assertEquals(listOf(1, 2), viewModel.state.value.lineup.map(LineupEntry::orderIndex))
    }

    /** Проверяет, что live-stage мутация обновляет статус lineup entry в state. */
    @Test
    fun updateLineupEntryStatusUpdatesLiveStageState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(
                defaultLineupEntry(id = "entry-1", orderIndex = 1, status = LineupEntryStatus.DRAFT),
                defaultLineupEntry(id = "entry-2", orderIndex = 2, status = LineupEntryStatus.UP_NEXT),
            ),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.updateLineupEntryStatus(
            eventId = "event-1",
            entryId = "entry-1",
            status = LineupEntryStatus.ON_STAGE,
        )
        advanceUntilIdle()

        assertEquals(LineupEntryStatus.ON_STAGE, viewModel.state.value.lineup.first { it.id == "entry-1" }.status)
        assertEquals("entry-1", viewModel.state.value.currentPerformer?.id)
        assertEquals("entry-2", viewModel.state.value.nextUpPerformer?.id)
    }

    /** Проверяет, что lifecycle activation подключает realtime feed и применяет public summary. */
    @Test
    fun activateLiveUpdatesAfterContextLoadAppliesIncomingSummary() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(
                defaultLineupEntry(id = "entry-1", orderIndex = 1, status = LineupEntryStatus.DRAFT),
                defaultLineupEntry(id = "entry-2", orderIndex = 2, status = LineupEntryStatus.UP_NEXT),
            ),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()
        viewModel.setLiveUpdatesActive(true)
        advanceUntilIdle()

        service.emitLiveUpdate(
            LineupLiveUpdate(
                type = LineupLiveUpdateType.STAGE_CURRENT_CHANGED,
                eventId = "event-1",
                occurredAtIso = "2026-03-25T17:20:00+03:00",
                reason = "live_state_changed",
                summary = LineupLiveSummary(
                    currentPerformer = LineupLiveEntry(
                        id = "entry-1",
                        comedianDisplayName = "Иван Смехов",
                        orderIndex = 1,
                        status = LineupEntryStatus.ON_STAGE,
                    ),
                    nextUp = null,
                    lineup = listOf(
                        LineupLiveEntry(
                            id = "entry-1",
                            comedianDisplayName = "Иван Смехов",
                            orderIndex = 1,
                            status = LineupEntryStatus.ON_STAGE,
                        ),
                        LineupLiveEntry(
                            id = "entry-2",
                            comedianDisplayName = "Мария Сетова",
                            orderIndex = 2,
                            status = LineupEntryStatus.DRAFT,
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(LineupEntryStatus.ON_STAGE, viewModel.state.value.lineup.first { it.id == "entry-1" }.status)
        assertEquals("entry-1", viewModel.state.value.currentPerformer?.id)
        assertNull(viewModel.state.value.nextUpPerformer)
    }

    /** Проверяет, что approval live update подтягивает organizer applications поверх public summary. */
    @Test
    fun applicationApprovedLiveUpdateRefreshesApplications() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()
        viewModel.setLiveUpdatesActive(true)
        advanceUntilIdle()

        service.approveApplicationForLiveUpdate(
            applicationId = "application-1",
            entryId = "entry-approved",
        )
        service.emitLiveUpdate(
            LineupLiveUpdate(
                type = LineupLiveUpdateType.LINEUP_CHANGED,
                eventId = "event-1",
                occurredAtIso = "2026-03-25T17:21:00+03:00",
                reason = "application_approved",
                summary = LineupLiveSummary(
                    currentPerformer = null,
                    nextUp = null,
                    lineup = listOf(
                        LineupLiveEntry(
                            id = "entry-approved",
                            comedianDisplayName = "Иван Смехов",
                            orderIndex = 1,
                            status = LineupEntryStatus.DRAFT,
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(ComedianApplicationStatus.APPROVED, viewModel.state.value.applications.single().status)
        assertEquals(listOf("entry-approved"), viewModel.state.value.lineup.map(LineupEntry::id))
    }

    /** Проверяет, что lifecycle deactivation останавливает применение новых realtime payload-ов. */
    @Test
    fun deactivateLiveUpdatesStopsApplyingIncomingEvents() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeLineupManagementService(
            initialLineup = mutableListOf(
                defaultLineupEntry(id = "entry-1", orderIndex = 1, status = LineupEntryStatus.DRAFT),
            ),
        )
        val viewModel = LineupViewModel(
            lineupManagementService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadOrganizerContext("event-1")
        advanceUntilIdle()
        viewModel.setLiveUpdatesActive(true)
        advanceUntilIdle()
        viewModel.setLiveUpdatesActive(false)
        advanceUntilIdle()

        service.emitLiveUpdate(
            LineupLiveUpdate(
                type = LineupLiveUpdateType.STAGE_CURRENT_CHANGED,
                eventId = "event-1",
                occurredAtIso = "2026-03-25T17:22:00+03:00",
                reason = "live_state_changed",
                summary = LineupLiveSummary(
                    currentPerformer = LineupLiveEntry(
                        id = "entry-1",
                        comedianDisplayName = "Иван Смехов",
                        orderIndex = 1,
                        status = LineupEntryStatus.ON_STAGE,
                    ),
                    nextUp = null,
                    lineup = listOf(
                        LineupLiveEntry(
                            id = "entry-1",
                            comedianDisplayName = "Иван Смехов",
                            orderIndex = 1,
                            status = LineupEntryStatus.ON_STAGE,
                        ),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(LineupEntryStatus.DRAFT, viewModel.state.value.lineup.single().status)
        assertNull(viewModel.state.value.currentPerformer)
    }
}

/**
 * Тестовый lineup service для shared ViewModel.
 */
private class FakeLineupManagementService(
    private val initialApplications: MutableList<ComedianApplication> = mutableListOf(defaultApplication()),
    initialLineup: MutableList<LineupEntry> = mutableListOf(defaultLineupEntry()),
) : LineupManagementService {
    /** Хранимый список заявок для тестовых сценариев. */
    private val applications: MutableList<ComedianApplication> = initialApplications

    /** Хранимый список lineup entries для тестовых сценариев. */
    private val lineupEntries: MutableList<LineupEntry> = initialLineup

    /** Per-event live update buses для shared realtime tests. */
    private val liveUpdates = mutableMapOf<String, MutableSharedFlow<LineupLiveUpdate>>()

    override suspend fun submitApplication(
        accessToken: String,
        eventId: String,
        note: String?,
    ): Result<ComedianApplication> {
        val created = defaultApplication(
            id = "application-created",
            status = ComedianApplicationStatus.SUBMITTED,
            note = note,
        )
        applications.add(created)
        return Result.success(created)
    }

    override suspend fun listEventApplications(
        accessToken: String,
        eventId: String,
    ): Result<List<ComedianApplication>> = Result.success(applications.toList())

    override suspend fun updateApplicationStatus(
        accessToken: String,
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ): Result<ComedianApplication> {
        val index = applications.indexOfFirst { it.id == applicationId }
        val updated = applications[index].copy(
            status = status,
            statusUpdatedAtIso = "2026-03-23T02:30:00+03:00",
            updatedAtIso = "2026-03-23T02:30:00+03:00",
        )
        applications[index] = updated
        if (status == ComedianApplicationStatus.APPROVED &&
            lineupEntries.none { it.applicationId == applicationId }
        ) {
            lineupEntries.add(
                defaultLineupEntry(
                    id = "entry-approved",
                    applicationId = applicationId,
                    orderIndex = lineupEntries.size + 1,
                ),
            )
        }
        return Result.success(updated)
    }

    override suspend fun listEventLineup(
        accessToken: String,
        eventId: String,
    ): Result<List<LineupEntry>> = Result.success(lineupEntries.sortedBy(LineupEntry::orderIndex))

    override suspend fun reorderLineup(
        accessToken: String,
        eventId: String,
        entries: List<LineupEntryOrderUpdate>,
    ): Result<List<LineupEntry>> {
        val reordered = entries.map { update ->
            lineupEntries.first { it.id == update.entryId }.copy(orderIndex = update.orderIndex)
        }.sortedBy(LineupEntry::orderIndex)
        lineupEntries.clear()
        lineupEntries.addAll(reordered)
        return Result.success(lineupEntries.toList())
    }

    override suspend fun updateLineupEntryStatus(
        accessToken: String,
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ): Result<List<LineupEntry>> {
        val index = lineupEntries.indexOfFirst { it.id == entryId }
        lineupEntries[index] = lineupEntries[index].copy(
            status = status,
            updatedAtIso = "2026-03-24T18:30:00+03:00",
        )
        return Result.success(lineupEntries.sortedBy(LineupEntry::orderIndex))
    }

    override fun observeEventLiveUpdates(eventId: String): Flow<LineupLiveUpdate> {
        return liveUpdates.getOrPut(eventId) {
            MutableSharedFlow(extraBufferCapacity = 8)
        }
    }

    /** Публикует тестовый realtime payload в flow соответствующего события. */
    fun emitLiveUpdate(update: LineupLiveUpdate) {
        liveUpdates.getOrPut(update.eventId) {
            MutableSharedFlow(extraBufferCapacity = 8)
        }.tryEmit(update)
    }

    /** Подготавливает service state к внешнему approval, который затем придет по live channel. */
    fun approveApplicationForLiveUpdate(
        applicationId: String,
        entryId: String,
    ) {
        val index = applications.indexOfFirst { it.id == applicationId }
        val updated = applications[index].copy(
            status = ComedianApplicationStatus.APPROVED,
            reviewedByUserId = "owner-1",
            reviewedByDisplayName = "Организатор",
            updatedAtIso = "2026-03-25T17:21:00+03:00",
            statusUpdatedAtIso = "2026-03-25T17:21:00+03:00",
        )
        applications[index] = updated
        if (lineupEntries.none { it.id == entryId }) {
            lineupEntries.add(
                defaultLineupEntry(
                    id = entryId,
                    applicationId = applicationId,
                    orderIndex = lineupEntries.size + 1,
                ),
            )
        }
    }
}

/** Собирает дефолтную заявку для тестов. */
private fun defaultApplication(
    id: String = "application-1",
    status: ComedianApplicationStatus = ComedianApplicationStatus.SUBMITTED,
    note: String? = "Хочу протестировать сет",
): ComedianApplication {
    return ComedianApplication(
        id = id,
        eventId = "event-1",
        comedianUserId = "comedian-1",
        comedianDisplayName = "Иван Смехов",
        comedianUsername = "smile",
        status = status,
        note = note,
        reviewedByUserId = if (status == ComedianApplicationStatus.SUBMITTED) null else "owner-1",
        reviewedByDisplayName = if (status == ComedianApplicationStatus.SUBMITTED) null else "Организатор",
        createdAtIso = "2026-03-23T01:00:00+03:00",
        updatedAtIso = "2026-03-23T01:00:00+03:00",
        statusUpdatedAtIso = "2026-03-23T01:00:00+03:00",
    )
}

/** Собирает дефолтный lineup entry для тестов. */
private fun defaultLineupEntry(
    id: String = "entry-1",
    applicationId: String? = "application-seeded",
    orderIndex: Int = 1,
    status: LineupEntryStatus = LineupEntryStatus.DRAFT,
): LineupEntry {
    return LineupEntry(
        id = id,
        eventId = "event-1",
        comedianUserId = "comedian-1",
        comedianDisplayName = "Иван Смехов",
        comedianUsername = "smile",
        applicationId = applicationId,
        orderIndex = orderIndex,
        status = status,
        notes = null,
        createdAtIso = "2026-03-23T01:10:00+03:00",
        updatedAtIso = "2026-03-23T01:10:00+03:00",
    )
}
