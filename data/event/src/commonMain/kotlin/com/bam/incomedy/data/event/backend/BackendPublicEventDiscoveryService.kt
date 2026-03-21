package com.bam.incomedy.data.event.backend

import com.bam.incomedy.domain.event.PublicEventDiscoveryFilter
import com.bam.incomedy.domain.event.PublicEventDiscoveryService
import com.bam.incomedy.domain.event.PublicEventSummary

/**
 * Backend-адаптер публичного audience discovery контракта.
 *
 * @property eventBackendApi HTTP-клиент event surface, содержащий и organizer-, и public-routes.
 */
class BackendPublicEventDiscoveryService(
    private val eventBackendApi: EventBackendApi,
) : PublicEventDiscoveryService {
    /** Загружает опубликованные public-события через backend discovery API. */
    override suspend fun listPublicEvents(
        filter: PublicEventDiscoveryFilter,
    ): Result<List<PublicEventSummary>> {
        return eventBackendApi.listPublicEvents(filter = filter)
    }
}
