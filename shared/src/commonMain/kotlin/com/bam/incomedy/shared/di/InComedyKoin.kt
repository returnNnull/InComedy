package com.bam.incomedy.shared.di

import com.bam.incomedy.data.auth.di.authDataModule
import com.bam.incomedy.data.event.di.eventDataModule
import com.bam.incomedy.data.lineup.di.lineupDataModule
import com.bam.incomedy.data.session.di.sessionDataModule
import com.bam.incomedy.data.ticketing.di.ticketingDataModule
import com.bam.incomedy.data.venue.di.venueDataModule
import com.bam.incomedy.feature.auth.di.authFeatureModule
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.event.EventViewModel
import com.bam.incomedy.feature.lineup.LineupViewModel
import com.bam.incomedy.feature.ticketing.TicketingViewModel
import com.bam.incomedy.feature.venue.VenueViewModel
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
        venueDataModule,
        eventDataModule,
        ticketingDataModule,
        lineupDataModule,
        authFeatureModule,
        module {
            single {
                SessionViewModel(
                    authViewModel = get(),
                    sessionContextService = get(),
                )
            }
            single {
                VenueViewModel(
                    venueManagementService = get(),
                    accessTokenProvider = { get<SessionViewModel>().state.value.accessToken },
                )
            }
            single {
                EventViewModel(
                    eventManagementService = get(),
                    venueManagementService = get(),
                    accessTokenProvider = { get<SessionViewModel>().state.value.accessToken },
                )
            }
            single {
                TicketingViewModel(
                    ticketingService = get(),
                    accessTokenProvider = { get<SessionViewModel>().state.value.accessToken },
                )
            }
            single {
                LineupViewModel(
                    lineupManagementService = get(),
                    accessTokenProvider = { get<SessionViewModel>().state.value.accessToken },
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

    /** Возвращает общую модель площадок и шаблонов зала. */
    fun getVenueViewModel(): VenueViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }

    /** Возвращает общую модель organizer events. */
    fun getEventViewModel(): EventViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }

    /** Возвращает общую модель audience/staff ticketing feature. */
    fun getTicketingViewModel(): TicketingViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }

    /** Возвращает общую модель comedian applications и organizer lineup. */
    fun getLineupViewModel(): LineupViewModel {
        init()
        return requireNotNull(koinApp).koin.get()
    }
}
