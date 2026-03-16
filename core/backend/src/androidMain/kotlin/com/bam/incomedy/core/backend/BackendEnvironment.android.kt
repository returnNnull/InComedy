package com.bam.incomedy.core.backend

/**
 * Android-конфигурация backend-окружения.
 *
 * Пока приложение работает только с production backend, поэтому base URL
 * зафиксирован на deployed домене.
 */
actual object BackendEnvironment {
    actual val baseUrl: String = "https://incomedy.ru"
}
