package com.bam.incomedy.data.ticketing.backend

import com.bam.incomedy.domain.ticketing.InventoryUnit
import com.bam.incomedy.domain.ticketing.SeatHold
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
