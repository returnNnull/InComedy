package com.bam.incomedy.shared.di

import com.bam.incomedy.data.auth.di.authDataModule
import com.bam.incomedy.feature.auth.di.authFeatureModule
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

object InComedyKoin {
    private var koinApp: KoinApplication? = null

    private val baseModules: List<Module> = listOf(
        authDataModule,
        authFeatureModule,
    )

    fun init(extraModules: List<Module> = emptyList()) {
        if (koinApp != null) return
        koinApp = startKoin {
            modules(baseModules + extraModules)
        }
    }

    fun getAuthViewModel(): AuthViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }
}
