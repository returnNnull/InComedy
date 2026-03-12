package com.bam.incomedy.feature.auth.di

import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import com.bam.incomedy.feature.auth.domain.SocialAuthService
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import org.koin.dsl.module

/** DI-модуль feature-слоя авторизации. */
val authFeatureModule = module {
    single {
        SocialAuthService(
            providers = getAll<SocialAuthProvider>(),
        )
    }

    single {
        AuthViewModel(
            socialAuthService = get(),
            sessionValidationService = get(),
            sessionTerminationService = get(),
        )
    }
}
