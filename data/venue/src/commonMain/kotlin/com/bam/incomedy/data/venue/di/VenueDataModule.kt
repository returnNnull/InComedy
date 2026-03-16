package com.bam.incomedy.data.venue.di

import com.bam.incomedy.data.venue.backend.BackendVenueManagementService
import com.bam.incomedy.data.venue.backend.VenueBackendApi
import com.bam.incomedy.domain.venue.VenueManagementService
import org.koin.dsl.module

/** DI-модуль data-слоя organizer venue management. */
val venueDataModule = module {
    single {
        VenueBackendApi()
    }
    single<VenueManagementService> {
        BackendVenueManagementService(venueBackendApi = get())
    }
}
