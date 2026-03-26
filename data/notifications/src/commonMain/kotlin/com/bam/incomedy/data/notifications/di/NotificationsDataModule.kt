package com.bam.incomedy.data.notifications.di

import com.bam.incomedy.data.notifications.backend.BackendNotificationService
import com.bam.incomedy.data.notifications.backend.NotificationBackendApi
import com.bam.incomedy.domain.notifications.NotificationService
import org.koin.dsl.module

/** DI-модуль data-слоя organizer announcements/event feed foundation. */
val notificationsDataModule = module {
    single {
        NotificationBackendApi()
    }
    single<NotificationService> {
        BackendNotificationService(notificationBackendApi = get())
    }
}
