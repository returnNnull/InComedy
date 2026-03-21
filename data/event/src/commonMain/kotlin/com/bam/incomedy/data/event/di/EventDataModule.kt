package com.bam.incomedy.data.event.di

import com.bam.incomedy.data.event.backend.BackendEventManagementService
import com.bam.incomedy.data.event.backend.BackendPublicEventDiscoveryService
import com.bam.incomedy.data.event.backend.EventBackendApi
import com.bam.incomedy.domain.event.EventManagementService
import com.bam.incomedy.domain.event.PublicEventDiscoveryService
import org.koin.dsl.module

/** DI-модуль data-слоя organizer event management. */
val eventDataModule = module {
    single {
        EventBackendApi()
    }
    single<EventManagementService> {
        BackendEventManagementService(eventBackendApi = get())
    }
    single<PublicEventDiscoveryService> {
        BackendPublicEventDiscoveryService(eventBackendApi = get())
    }
}
