package com.bam.incomedy.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.feature.donations.viewmodel.DonationsAndroidViewModel
import com.bam.incomedy.feature.event.viewmodel.EventAndroidViewModel
import com.bam.incomedy.feature.lineup.viewmodel.LineupAndroidViewModel
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import com.bam.incomedy.feature.ticketing.viewmodel.TicketingAndroidViewModel
import com.bam.incomedy.feature.venue.viewmodel.VenueAndroidViewModel

/**
 * Набор явных фабрик Android `ViewModel`, который исключает зависимость от дефолтной
 * рефлексивной инициализации `AndroidViewModel`.
 */
object AndroidViewModelFactories {
    /**
     * Возвращает фабрику для Android-адаптера авторизации.
     *
     * @param application Android application context, необходимый `AuthAndroidViewModel`.
     */
    fun auth(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(AuthAndroidViewModel::class.java) {
            AuthAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера общей сессии.
     *
     * @param application Android application context, необходимый `SessionAndroidViewModel`.
     */
    fun session(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(SessionAndroidViewModel::class.java) {
            SessionAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера organizer venue feature.
     *
     * @param application Android application context, необходимый `VenueAndroidViewModel`.
     */
    fun venue(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(VenueAndroidViewModel::class.java) {
            VenueAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера organizer event feature.
     *
     * @param application Android application context, необходимый `EventAndroidViewModel`.
     */
    fun event(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(EventAndroidViewModel::class.java) {
            EventAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера audience/staff ticketing feature.
     *
     * @param application Android application context, необходимый `TicketingAndroidViewModel`.
     */
    fun ticketing(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(TicketingAndroidViewModel::class.java) {
            TicketingAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера donation overview и comedian payout surface.
     *
     * @param application Android application context, необходимый `DonationsAndroidViewModel`.
     */
    fun donations(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(DonationsAndroidViewModel::class.java) {
            DonationsAndroidViewModel(application)
        }
    }

    /**
     * Возвращает фабрику для Android-адаптера comedian applications и organizer lineup feature.
     *
     * @param application Android application context, необходимый `LineupAndroidViewModel`.
     */
    fun lineup(application: Application): ViewModelProvider.Factory {
        return singleViewModelFactory(LineupAndroidViewModel::class.java) {
            LineupAndroidViewModel(application)
        }
    }

    /**
     * Создает фабрику для одного конкретного типа `ViewModel`.
     *
     * @param expectedClass Поддерживаемый тип `ViewModel`.
     * @param creator Лямбда создания экземпляра нужного типа.
     */
    private fun <T : ViewModel> singleViewModelFactory(
        expectedClass: Class<T>,
        creator: () -> T,
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            /**
             * Создает экземпляр поддерживаемой `ViewModel`.
             *
             * @param modelClass Запрошенный класс `ViewModel`.
             * @param extras Дополнительные параметры создания из Android lifecycle.
             */
            override fun <VM : ViewModel> create(
                modelClass: Class<VM>,
                extras: CreationExtras,
            ): VM {
                if (!modelClass.isAssignableFrom(expectedClass)) {
                    throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
                }
                @Suppress("UNCHECKED_CAST")
                return creator() as VM
            }
        }
    }
}
