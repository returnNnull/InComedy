package com.bam.incomedy.feature.notifications

import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
import com.bam.incomedy.domain.notifications.NotificationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit-тесты shared notifications feature model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @Test
    fun loadEventFeedStoresSortedAnnouncements() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeNotificationService()
        val viewModel = NotificationsViewModel(
            notificationService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadEventFeed("event-1")
        advanceUntilIdle()

        assertEquals("event-1", viewModel.state.value.selectedEventId)
        assertEquals(listOf("announcement-2", "announcement-1"), viewModel.state.value.announcements.map(EventAnnouncement::id))
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun createEventAnnouncementPrependsCreatedEntry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeNotificationService()
        val viewModel = NotificationsViewModel(
            notificationService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadEventFeed("event-1")
        advanceUntilIdle()
        viewModel.createEventAnnouncement(
            eventId = "event-1",
            message = "  Начинаем через 10 минут  ",
        )
        advanceUntilIdle()

        assertEquals("Начинаем через 10 минут", service.lastCreatedMessage)
        assertEquals("announcement-3", viewModel.state.value.announcements.first().id)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun createEventAnnouncementRequiresAccessToken() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeNotificationService()
        val viewModel = NotificationsViewModel(
            notificationService = service,
            accessTokenProvider = { null },
            dispatcher = dispatcher,
        )

        viewModel.createEventAnnouncement(
            eventId = "event-1",
            message = "Начинаем через 10 минут",
        )
        advanceUntilIdle()

        assertEquals("Нет активной сессии для публикации announcement-а", viewModel.state.value.errorMessage)
        assertEquals(0, service.createCalls)
    }

    @Test
    fun createEventAnnouncementRejectsBlankMessageLocally() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = FakeNotificationService()
        val viewModel = NotificationsViewModel(
            notificationService = service,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.createEventAnnouncement(
            eventId = "event-1",
            message = "   ",
        )
        advanceUntilIdle()

        assertEquals("Текст announcement-а не должен быть пустым", viewModel.state.value.errorMessage)
        assertEquals(0, service.createCalls)
    }
}

private class FakeNotificationService : NotificationService {
    private val announcements = mutableListOf(
        announcement(
            id = "announcement-1",
            createdAtIso = "2026-03-26T21:00:00+03:00",
            message = "Запуск гостей через 15 минут",
        ),
        announcement(
            id = "announcement-2",
            createdAtIso = "2026-03-26T21:05:00+03:00",
            message = "Комик уже на площадке",
        ),
    )

    var createCalls: Int = 0
        private set

    var lastCreatedMessage: String? = null
        private set

    override suspend fun listPublicEventAnnouncements(
        eventId: String,
    ): Result<List<EventAnnouncement>> {
        return Result.success(announcements.filter { it.eventId == eventId })
    }

    override suspend fun createEventAnnouncement(
        accessToken: String,
        eventId: String,
        message: String,
    ): Result<EventAnnouncement> {
        createCalls += 1
        lastCreatedMessage = message
        val createdAnnouncement = announcement(
            id = "announcement-${announcements.size + 1}",
            eventId = eventId,
            createdAtIso = "2026-03-26T21:10:00+03:00",
            message = message,
            authorRole = EventAnnouncementAuthorRole.HOST,
        )
        announcements.add(createdAnnouncement)
        return Result.success(createdAnnouncement)
    }
}

private fun announcement(
    id: String,
    eventId: String = "event-1",
    createdAtIso: String,
    message: String,
    authorRole: EventAnnouncementAuthorRole = EventAnnouncementAuthorRole.ORGANIZER,
): EventAnnouncement {
    return EventAnnouncement(
        id = id,
        eventId = eventId,
        message = message,
        authorRole = authorRole,
        createdAtIso = createdAtIso,
    )
}
