package com.bam.incomedy.feature.auth.di

import com.bam.incomedy.domain.auth.SocialAuthProvider
import com.bam.incomedy.domain.auth.SocialAuthService
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
            credentialAuthService = get(),
            socialAuthService = get(),
            sessionValidationService = get(),
            sessionTerminationService = get(),
        )
    }
}
