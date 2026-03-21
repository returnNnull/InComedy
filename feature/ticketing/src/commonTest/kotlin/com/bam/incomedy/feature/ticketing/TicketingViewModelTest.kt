package com.bam.incomedy.feature.ticketing

import com.bam.incomedy.domain.ticketing.InventoryUnit
import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.IssuedTicketStatus
import com.bam.incomedy.domain.ticketing.SeatHold
import com.bam.incomedy.domain.ticketing.SeatHoldStatus
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.domain.ticketing.TicketCheckInResultCode
import com.bam.incomedy.domain.ticketing.TicketCheckoutProvider
import com.bam.incomedy.domain.ticketing.TicketCheckoutSession
import com.bam.incomedy.domain.ticketing.TicketCheckoutSessionStatus
import com.bam.incomedy.domain.ticketing.TicketOrder
import com.bam.incomedy.domain.ticketing.TicketOrderLine
import com.bam.incomedy.domain.ticketing.TicketOrderStatus
import com.bam.incomedy.domain.ticketing.TicketingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit-тесты shared ticketing feature model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketingViewModelTest {
    /** Проверяет загрузку списка билетов текущего пользователя. */
    @Test
    fun loadTicketsStoresSortedTickets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = TicketingViewModel(
            ticketingService = FakeTicketingService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.loadTickets()
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.tickets.size)
        assertEquals(IssuedTicketStatus.ISSUED, viewModel.state.value.tickets.first().status)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    /** Проверяет локальную validation ошибку до network roundtrip. */
    @Test
    fun scanTicketShowsValidationErrorForBlankPayload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = TicketingViewModel(
            ticketingService = FakeTicketingService(),
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.scanTicket("   ")
        advanceUntilIdle()

        assertEquals("Введите QR payload для проверки билета", viewModel.state.value.errorMessage)
    }

    /** Проверяет сохранение результата server-side check-in. */
    @Test
    fun scanTicketStoresLastCheckInResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ticketingService = FakeTicketingService()
        val viewModel = TicketingViewModel(
            ticketingService = ticketingService,
            accessTokenProvider = { "access-token" },
            dispatcher = dispatcher,
        )

        viewModel.scanTicket("incomedy.ticket.v1:ticket-1")
        advanceUntilIdle()

        assertEquals("incomedy.ticket.v1:ticket-1", ticketingService.lastScannedPayload)
        assertEquals(TicketCheckInResultCode.CHECKED_IN, viewModel.state.value.lastCheckInResult?.resultCode)
        assertEquals(IssuedTicketStatus.CHECKED_IN, viewModel.state.value.lastCheckInResult?.ticket?.status)
    }

    /** Проверяет ошибку, если feature вызван без активного access token. */
    @Test
    fun loadTicketsShowsErrorWhenSessionIsMissing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = TicketingViewModel(
            ticketingService = FakeTicketingService(),
            accessTokenProvider = { null },
            dispatcher = dispatcher,
        )

        viewModel.loadTickets()
        advanceUntilIdle()

        assertEquals("Нет активной сессии для работы с билетами", viewModel.state.value.errorMessage)
    }
}

/**
 * Тестовый ticketing service для shared ViewModel.
 */
private class FakeTicketingService : TicketingService {
    var lastScannedPayload: String? = null

    private val tickets = mutableListOf(
        defaultIssuedTicket(
            id = "ticket-checked",
            status = IssuedTicketStatus.CHECKED_IN,
            issuedAtIso = "2026-04-01T18:00:00+03:00",
            checkedInAtIso = "2026-04-01T18:30:00+03:00",
        ),
        defaultIssuedTicket(
            id = "ticket-issued",
            status = IssuedTicketStatus.ISSUED,
            issuedAtIso = "2026-04-01T19:00:00+03:00",
            qrPayload = "incomedy.ticket.v1:ticket-1",
        ),
    )

    override suspend fun listPublicInventory(eventId: String): Result<List<InventoryUnit>> = Result.success(emptyList())

    override suspend fun listInventory(
        accessToken: String,
        eventId: String,
    ): Result<List<InventoryUnit>> = Result.success(emptyList())

    override suspend fun createSeatHold(
        accessToken: String,
        eventId: String,
        inventoryRef: String,
    ): Result<SeatHold> {
        return Result.success(
            SeatHold(
                id = "hold-1",
                eventId = eventId,
                inventoryUnitId = "inventory-1",
                inventoryRef = inventoryRef,
                expiresAtIso = "2026-04-01T19:10:00+03:00",
                status = SeatHoldStatus.ACTIVE,
            ),
        )
    }

    override suspend fun createTicketOrder(
        accessToken: String,
        eventId: String,
        holdIds: List<String>,
    ): Result<TicketOrder> {
        return Result.success(
            TicketOrder(
                id = "order-1",
                eventId = eventId,
                status = TicketOrderStatus.AWAITING_PAYMENT,
                currency = "RUB",
                totalMinor = 2_500,
                checkoutExpiresAtIso = "2026-04-01T19:10:00+03:00",
                lines = listOf(
                    TicketOrderLine(
                        inventoryUnitId = "inventory-1",
                        inventoryRef = "seat-a1",
                        label = "Ряд A · Место 1",
                        priceMinor = 2_500,
                        currency = "RUB",
                    ),
                ),
            ),
        )
    }

    override suspend fun getTicketOrder(
        accessToken: String,
        orderId: String,
    ): Result<TicketOrder> {
        return createTicketOrder(accessToken, "event-1", listOf("hold-1"))
    }

    override suspend fun listMyTickets(
        accessToken: String,
    ): Result<List<IssuedTicket>> = Result.success(tickets.toList())

    override suspend fun startTicketCheckout(
        accessToken: String,
        eventId: String,
        orderId: String,
    ): Result<TicketCheckoutSession> {
        return Result.success(
            TicketCheckoutSession(
                id = "checkout-1",
                orderId = orderId,
                eventId = eventId,
                provider = TicketCheckoutProvider.YOOKASSA,
                status = TicketCheckoutSessionStatus.PENDING_REDIRECT,
                confirmationUrl = "https://example.com/confirm",
                checkoutExpiresAtIso = "2026-04-01T19:10:00+03:00",
            ),
        )
    }

    override suspend fun scanTicket(
        accessToken: String,
        qrPayload: String,
    ): Result<TicketCheckInResult> {
        lastScannedPayload = qrPayload
        val issued = tickets.first { it.qrPayload == qrPayload }
        val checkedIn = issued.copy(
            status = IssuedTicketStatus.CHECKED_IN,
            checkedInAtIso = "2026-04-01T19:05:00+03:00",
            checkedInByUserId = "checker-1",
            qrPayload = null,
        )
        return Result.success(
            TicketCheckInResult(
                resultCode = TicketCheckInResultCode.CHECKED_IN,
                ticket = checkedIn,
            ),
        )
    }

    override suspend fun releaseSeatHold(
        accessToken: String,
        holdId: String,
    ): Result<SeatHold> {
        return Result.success(
            SeatHold(
                id = holdId,
                eventId = "event-1",
                inventoryUnitId = "inventory-1",
                inventoryRef = "seat-a1",
                expiresAtIso = "2026-04-01T19:10:00+03:00",
                status = SeatHoldStatus.RELEASED,
            ),
        )
    }
}

/**
 * Возвращает тестовый выданный билет для сценариев shared ViewModel.
 */
private fun defaultIssuedTicket(
    id: String = "ticket-1",
    status: IssuedTicketStatus = IssuedTicketStatus.ISSUED,
    issuedAtIso: String = "2026-04-01T18:00:00+03:00",
    checkedInAtIso: String? = null,
    qrPayload: String? = "incomedy.ticket.v1:ticket-1",
): IssuedTicket {
    return IssuedTicket(
        id = id,
        orderId = "order-1",
        eventId = "event-1",
        inventoryUnitId = "inventory-1",
        inventoryRef = "seat-a1",
        label = "Ряд A · Место 1",
        status = status,
        qrPayload = qrPayload,
        issuedAtIso = issuedAtIso,
        checkedInAtIso = checkedInAtIso,
        checkedInByUserId = checkedInAtIso?.let { "checker-1" },
    )
}
