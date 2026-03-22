package com.bam.incomedy.data.lineup.di

import com.bam.incomedy.data.lineup.backend.BackendLineupManagementService
import com.bam.incomedy.data.lineup.backend.LineupBackendApi
import com.bam.incomedy.domain.lineup.LineupManagementService
import org.koin.dsl.module

/** DI-модуль data-слоя comedian applications и organizer lineup. */
val lineupDataModule = module {
    single {
        LineupBackendApi()
    }
    single<LineupManagementService> {
        BackendLineupManagementService(lineupBackendApi = get())
    }
}
