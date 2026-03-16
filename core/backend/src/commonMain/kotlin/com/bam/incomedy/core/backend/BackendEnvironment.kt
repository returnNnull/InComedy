package com.bam.incomedy.core.backend

/**
 * Платформенная конфигурация backend-окружения приложения.
 *
 * Объект живет вне `data:auth`, потому что один и тот же base URL используется
 * и auth transport, и post-auth session context, и не должен привязывать эти
 * bounded context друг к другу.
 *
 * @property baseUrl Базовый URL основного InComedy backend API.
 */
expect object BackendEnvironment {
    val baseUrl: String
}
