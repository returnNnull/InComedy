package com.bam.incomedy.shared.di

import com.bam.incomedy.data.auth.di.authDataModule
import com.bam.incomedy.data.session.di.sessionDataModule
import com.bam.incomedy.feature.auth.di.authFeatureModule
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.shared.session.SessionViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Точка входа в общий Koin-контейнер приложения.
 */
object InComedyKoin {
    /** Удерживает запущенный экземпляр Koin-приложения. */
    private var koinApp: KoinApplication? = null

    /** Базовый набор общих модулей DI для auth и session слоев. */
    private val baseModules: List<Module> = listOf(
        authDataModule,
        sessionDataModule,
        authFeatureModule,
        module {
            single {
                SessionViewModel(
                    authViewModel = get(),
                    sessionContextService = get(),
                )
            }
        },
    )

    /** Инициализирует Koin-контейнер, если он еще не был создан. */
    fun init(extraModules: List<Module> = emptyList()) {
        if (koinApp != null) return
        koinApp = startKoin {
            modules(baseModules + extraModules)
        }
    }

    /** Возвращает общий auth `ViewModel`. */
    fun getAuthViewModel(): AuthViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }

    /** Возвращает общую модель сессии. */
    fun getSessionViewModel(): SessionViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }
}
