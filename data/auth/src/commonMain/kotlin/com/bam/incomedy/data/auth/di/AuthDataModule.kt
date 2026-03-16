package com.bam.incomedy.data.auth.di

import com.bam.incomedy.data.auth.backend.AuthBackendApi
import com.bam.incomedy.data.auth.backend.BackendCredentialAuthService
import com.bam.incomedy.data.auth.backend.BackendSessionTerminationService
import com.bam.incomedy.data.auth.backend.BackendSessionValidationService
import com.bam.incomedy.data.auth.providers.VkAuthProvider
import com.bam.incomedy.domain.auth.CredentialAuthService
import com.bam.incomedy.domain.auth.SessionTerminationService
import com.bam.incomedy.domain.auth.SessionValidationService
import com.bam.incomedy.domain.auth.SocialAuthProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/** DI-модуль data-слоя авторизации и session lifecycle, не включающий post-auth context. */
val authDataModule = module {
    single {
        AuthBackendApi()
    }
    single<CredentialAuthService> {
        BackendCredentialAuthService(backendApi = get())
    }
    single<SessionValidationService> {
        BackendSessionValidationService(authBackendApi = get())
    }
    single<SessionTerminationService> {
        BackendSessionTerminationService(authBackendApi = get())
    }

    single {
        VkAuthProvider(backendApi = get())
    } bind SocialAuthProvider::class
}
