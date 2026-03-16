package com.bam.incomedy.data.venue.backend

import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateDraft
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.domain.venue.VenueDraft
import com.bam.incomedy.domain.venue.VenueManagementService

/**
 * Backend-адаптер domain-контракта venue management.
 *
 * @property venueBackendApi HTTP-клиент organizer venue surface.
 */
class BackendVenueManagementService(
    private val venueBackendApi: VenueBackendApi,
) : VenueManagementService {
    /** Загружает список площадок текущей сессии через backend API. */
    override suspend fun listVenues(accessToken: String): Result<List<OrganizerVenue>> {
        return venueBackendApi.listVenues(accessToken = accessToken)
    }

    /** Создает площадку через backend API. */
    override suspend fun createVenue(
        accessToken: String,
        draft: VenueDraft,
    ): Result<OrganizerVenue> {
        return venueBackendApi.createVenue(
            accessToken = accessToken,
            draft = draft,
        )
    }

    /** Создает hall template через backend API. */
    override suspend fun createHallTemplate(
        accessToken: String,
        venueId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate> {
        return venueBackendApi.createHallTemplate(
            accessToken = accessToken,
            venueId = venueId,
            draft = draft,
        )
    }

    /** Обновляет hall template через backend API. */
    override suspend fun updateHallTemplate(
        accessToken: String,
        templateId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate> {
        return venueBackendApi.updateHallTemplate(
            accessToken = accessToken,
            templateId = templateId,
            draft = draft,
        )
    }

    /** Клонирует hall template через backend API. */
    override suspend fun cloneHallTemplate(
        accessToken: String,
        templateId: String,
        clonedName: String?,
    ): Result<HallTemplate> {
        return venueBackendApi.cloneHallTemplate(
            accessToken = accessToken,
            templateId = templateId,
            clonedName = clonedName,
        )
    }
}
