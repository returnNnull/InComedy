package com.bam.incomedy.data.session.di

import com.bam.incomedy.data.session.backend.BackendSessionContextService
import com.bam.incomedy.data.session.backend.SessionBackendApi
import com.bam.incomedy.domain.session.SessionContextService
import org.koin.dsl.module

/** DI-модуль data-слоя post-auth session context. */
val sessionDataModule = module {
    single {
        SessionBackendApi()
    }
    single<SessionContextService> {
        BackendSessionContextService(sessionBackendApi = get())
    }
}
