package com.bam.incomedy.data.donations.di

import com.bam.incomedy.data.donations.backend.BackendDonationService
import com.bam.incomedy.data.donations.backend.DonationBackendApi
import com.bam.incomedy.domain.donations.DonationService
import org.koin.dsl.module

/** DI-модуль data-слоя donations/payout foundation. */
val donationsDataModule = module {
    single {
        DonationBackendApi()
    }
    single<DonationService> {
        BackendDonationService(donationBackendApi = get())
    }
}
