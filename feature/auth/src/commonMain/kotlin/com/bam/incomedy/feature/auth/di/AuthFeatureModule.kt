package com.bam.incomedy.feature.auth.di

import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import org.koin.dsl.module

val authFeatureModule = module {
    single {
        SocialAuthService(
            providers = getAll<SocialAuthProvider>(),
        )
    }

    factory {
        AuthViewModel(
            socialAuthService = get(),
            sessionValidationService = get(),
            sessionTerminationService = get(),
        )
    }
}
