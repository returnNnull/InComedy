package com.bam.incomedy.data.auth.backend

/**
 * Минимальный gateway для запуска и завершения Telegram auth через backend.
 */
interface TelegramAuthGateway {
    /** Запрашивает у backend-а официальный Telegram launch URL и server-issued state. */
    suspend fun startTelegramAuth(): Result<TelegramAuthLaunch>

    /** Завершает Telegram auth по callback `code/state` и возвращает внутреннюю сессию. */
    suspend fun verifyTelegram(payload: TelegramVerifyPayload): Result<TelegramBackendSession>
}
