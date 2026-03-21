package com.bam.incomedy.data.ticketing.backend

import com.bam.incomedy.domain.ticketing.InventoryUnit
import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.SeatHold
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.domain.ticketing.TicketCheckoutSession
import com.bam.incomedy.domain.ticketing.TicketOrder
import com.bam.incomedy.domain.ticketing.TicketingService

/**
 * Backend-реализация `TicketingService`.
 *
 * Адаптер оставляет transport-слой внутри `TicketingBackendApi`, а наружу отдает только доменные
 * модели ticketing foundation slice-а.
 */
class BackendTicketingService(
    private val ticketingBackendApi: TicketingBackendApi,
) : TicketingService {
    /** Загружает публичный инвентарь события через backend API. */
    override suspend fun listPublicInventory(
        eventId: String,
    ): Result<List<InventoryUnit>> {
        return ticketingBackendApi.listPublicInventory(
            eventId = eventId,
        )
    }

    /** Загружает текущий инвентарь события через backend API. */
    override suspend fun listInventory(
        accessToken: String,
        eventId: String,
    ): Result<List<InventoryUnit>> {
        return ticketingBackendApi.listInventory(
            accessToken = accessToken,
            eventId = eventId,
        )
    }

    /** Создает hold через backend API. */
    override suspend fun createSeatHold(
        accessToken: String,
        eventId: String,
        inventoryRef: String,
    ): Result<SeatHold> {
        return ticketingBackendApi.createSeatHold(
            accessToken = accessToken,
            eventId = eventId,
            inventoryRef = inventoryRef,
        )
    }

    /** Создает checkout order через backend API. */
    override suspend fun createTicketOrder(
        accessToken: String,
        eventId: String,
        holdIds: List<String>,
    ): Result<TicketOrder> {
        return ticketingBackendApi.createTicketOrder(
            accessToken = accessToken,
            eventId = eventId,
            holdIds = holdIds,
        )
    }

    /** Возвращает текущий checkout order через backend API. */
    override suspend fun getTicketOrder(
        accessToken: String,
        orderId: String,
    ): Result<TicketOrder> {
        return ticketingBackendApi.getTicketOrder(
            accessToken = accessToken,
            orderId = orderId,
        )
    }

    /** Возвращает список билетов текущего пользователя через backend API. */
    override suspend fun listMyTickets(
        accessToken: String,
    ): Result<List<IssuedTicket>> {
        return ticketingBackendApi.listMyTickets(
            accessToken = accessToken,
        )
    }

    /** Стартует внешний checkout через backend API. */
    override suspend fun startTicketCheckout(
        accessToken: String,
        eventId: String,
        orderId: String,
    ): Result<TicketCheckoutSession> {
        return ticketingBackendApi.startTicketCheckout(
            accessToken = accessToken,
            eventId = eventId,
            orderId = orderId,
        )
    }

    /** Отправляет QR на серверную проверку для check-in сценария. */
    override suspend fun scanTicket(
        accessToken: String,
        qrPayload: String,
    ): Result<TicketCheckInResult> {
        return ticketingBackendApi.scanTicket(
            accessToken = accessToken,
            qrPayload = qrPayload,
        )
    }

    /** Освобождает hold через backend API. */
    override suspend fun releaseSeatHold(
        accessToken: String,
        holdId: String,
    ): Result<SeatHold> {
        return ticketingBackendApi.releaseSeatHold(
            accessToken = accessToken,
            holdId = holdId,
        )
    }
}
