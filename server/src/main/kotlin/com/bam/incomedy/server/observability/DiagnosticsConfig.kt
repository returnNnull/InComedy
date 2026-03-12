package com.bam.incomedy.server.observability

/**
 * Конфигурация operator-only доступа к серверной диагностике.
 *
 * @property accessToken Секретный токен для защищенного чтения диагностических событий.
 * @property retentionLimit Максимальное число последних событий, удерживаемых в памяти.
 */
data class DiagnosticsConfig(
    val accessToken: String,
    val retentionLimit: Int,
) {
    companion object {
        /**
         * Собирает конфигурацию диагностики из окружения.
         *
         * `null` означает, что retrieval endpoint отключен.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): DiagnosticsConfig? {
            val accessToken = env["DIAGNOSTICS_ACCESS_TOKEN"]?.trim().orEmpty()
            if (accessToken.isBlank()) {
                return null
            }
            val retentionLimit = env["DIAGNOSTICS_RETENTION_LIMIT"]?.toIntOrNull()
                ?.coerceIn(MIN_RETENTION_LIMIT, MAX_RETENTION_LIMIT)
                ?: DEFAULT_RETENTION_LIMIT
            return DiagnosticsConfig(
                accessToken = accessToken,
                retentionLimit = retentionLimit,
            )
        }
    }
}

/** Минимально допустимый размер in-memory retention для диагностики. */
const val MIN_RETENTION_LIMIT = 100

/** Значение retention по умолчанию для локального/staging/production troubleshooting. */
const val DEFAULT_RETENTION_LIMIT = 1000

/** Верхняя граница in-memory retention для защиты памяти процесса. */
const val MAX_RETENTION_LIMIT = 5000
