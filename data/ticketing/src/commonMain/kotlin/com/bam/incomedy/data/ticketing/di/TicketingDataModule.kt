package com.bam.incomedy.data.ticketing.di

import com.bam.incomedy.data.ticketing.backend.BackendTicketingService
import com.bam.incomedy.data.ticketing.backend.TicketingBackendApi
import com.bam.incomedy.domain.ticketing.TicketingService
import org.koin.dsl.module

/** DI-модуль data-слоя ticketing foundation. */
val ticketingDataModule = module {
    single {
        TicketingBackendApi()
    }
    single<TicketingService> {
        BackendTicketingService(ticketingBackendApi = get())
    }
}
