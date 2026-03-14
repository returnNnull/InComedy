package com.bam.incomedy.server.config

/**
 * Корневой runtime-конфиг backend-приложения.
 *
 * @property database Настройки подключения к PostgreSQL.
 * @property jwt Настройки внутренних JWT-сессий.
 * @property redis Настройки Redis rate limiter-а, если он включен.
 */
data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val vkId: VkIdConfig?,
    val iosAssociatedDomains: IosAssociatedDomainsConfig?,
    val redis: RedisConfig?,
) {
    /** Загружает и валидирует runtime-конфиг приложения из environment variables. */
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val dbUrl = env.require("DB_URL")
            val dbHost = extractPostgresHost(dbUrl)
            val dbIsLocal = dbHost?.isLocalHostName() == true
            val dbAllowInsecure = env["DB_ALLOW_INSECURE"]?.toBooleanStrictOrNull() ?: false
            val dbSslMode = env["DB_SSL_MODE"] ?: if (dbIsLocal) "disable" else "require"
            if (!dbIsLocal && dbSslMode.equals("disable", ignoreCase = true) && !dbAllowInsecure) {
                error("Refusing insecure remote DB connection. Set DB_SSL_MODE=require or DB_ALLOW_INSECURE=true")
            }

            val redisConfig = env["REDIS_URL"]?.takeIf { it.isNotBlank() }?.let { rawUrl ->
                val redisAllowInsecure = env["REDIS_ALLOW_INSECURE"]?.toBooleanStrictOrNull() ?: false
                val redisIsSecure = rawUrl.startsWith("rediss://", ignoreCase = true)
                val redisHost = extractRedisHost(rawUrl)
                val redisIsLocal = redisHost?.isLocalHostName() == true
                if (!redisIsSecure && !redisIsLocal && !redisAllowInsecure) {
                    error("Refusing insecure remote Redis connection. Use rediss:// or set REDIS_ALLOW_INSECURE=true")
                }
                RedisConfig(
                    url = rawUrl,
                    allowInsecure = redisAllowInsecure,
                )
            }
            return AppConfig(
                database = DatabaseConfig(
                    jdbcUrl = withDbSslMode(dbUrl, dbSslMode),
                    username = env.require("DB_USER"),
                    password = env.require("DB_PASSWORD"),
                    sslMode = dbSslMode,
                    allowInsecure = dbAllowInsecure,
                ),
                jwt = JwtConfig(
                    issuer = env["JWT_ISSUER"] ?: "incomedy-server",
                    secret = env.require("JWT_SECRET"),
                    accessTtlSeconds = env["JWT_ACCESS_TTL_SECONDS"]?.toLongOrNull() ?: 3600L,
                    refreshTtlSeconds = env["JWT_REFRESH_TTL_SECONDS"]?.toLongOrNull() ?: 2592000L,
                ),
                vkId = env.vkIdConfig(
                    fallbackStateSecret = env.require("JWT_SECRET"),
                ),
                iosAssociatedDomains = env.iosAssociatedDomainsConfig(),
                redis = redisConfig,
            )
        }
    }
}

/**
 * Конфиг подключения к PostgreSQL.
 *
 * @property jdbcUrl JDBC URL с уже примененным `sslmode`.
 * @property username Пользователь БД.
 * @property password Пароль БД.
 * @property sslMode Активный режим TLS для PostgreSQL.
 * @property allowInsecure Временный флаг допуска insecure remote DB connection.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val sslMode: String,
    val allowInsecure: Boolean,
)

/**
 * Конфиг внутренних JWT-сессий backend-а.
 *
 * @property issuer Значение `iss` для access/refresh token-ов.
 * @property secret HMAC-секрет подписи внутренних JWT.
 * @property accessTtlSeconds TTL access token в секундах.
 * @property refreshTtlSeconds TTL refresh token в секундах.
 */
data class JwtConfig(
    val issuer: String,
    val secret: String,
    val accessTtlSeconds: Long,
    val refreshTtlSeconds: Long,
)

/**
 * Конфиг Redis rate limiter-а.
 *
 * @property url Полный Redis/Rediss URL.
 * @property allowInsecure Временный флаг допуска insecure remote Redis connection.
 */
data class RedisConfig(
    val url: String,
    val allowInsecure: Boolean,
)

data class VkIdConfig(
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val stateSecret: String,
    val stateTtlSeconds: Long,
)

data class IosAssociatedDomainsConfig(
    val appIds: List<String>,
    val paths: List<String>,
)

/** Извлекает обязательную environment variable или завершает startup с понятной ошибкой. */
private fun Map<String, String>.require(name: String): String {
    return this[name]?.takeIf { it.isNotBlank() }
        ?: error("Environment variable '$name' is required")
}

/** Добавляет `sslmode` к PostgreSQL JDBC URL, если он еще явно не указан. */
private fun withDbSslMode(jdbcUrl: String, sslMode: String): String {
    if ("sslmode=" in jdbcUrl.lowercase()) return jdbcUrl
    val delimiter = if ('?' in jdbcUrl) "&" else "?"
    return "$jdbcUrl${delimiter}sslmode=$sslMode"
}

private fun Map<String, String>.vkIdConfig(fallbackStateSecret: String): VkIdConfig? {
    val clientId = this["VK_ID_CLIENT_ID"]?.trim()
    val redirectUri = this["VK_ID_REDIRECT_URI"]?.trim()
    if (clientId.isNullOrBlank() && redirectUri.isNullOrBlank()) {
        return null
    }
    require(!clientId.isNullOrBlank()) { "Environment variable 'VK_ID_CLIENT_ID' is required when VK ID auth is enabled" }
    require(!redirectUri.isNullOrBlank()) { "Environment variable 'VK_ID_REDIRECT_URI' is required when VK ID auth is enabled" }
    return VkIdConfig(
        clientId = clientId,
        redirectUri = redirectUri,
        scope = this["VK_ID_SCOPE"]?.trim().takeUnless { it.isNullOrBlank() } ?: "vkid.personal_info",
        stateSecret = this["VK_ID_STATE_SECRET"]?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackStateSecret,
        stateTtlSeconds = this["VK_ID_STATE_TTL_SECONDS"]?.toLongOrNull() ?: 600L,
    )
}

private fun Map<String, String>.iosAssociatedDomainsConfig(): IosAssociatedDomainsConfig? {
    val rawAppIds = this["IOS_ASSOCIATED_DOMAIN_APP_IDS"]?.trim().orEmpty()
    if (rawAppIds.isBlank()) return null
    val appIds = rawAppIds.split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
    require(appIds.isNotEmpty()) {
        "Environment variable 'IOS_ASSOCIATED_DOMAIN_APP_IDS' must contain at least one app id when associated domains are enabled"
    }
    return IosAssociatedDomainsConfig(
        appIds = appIds,
        paths = listOf(
            "/auth/vk/callback",
            "/auth/vk/callback/*",
        ),
    )
}

/** Извлекает hostname из PostgreSQL JDBC URL для security policy checks. */
private fun extractPostgresHost(jdbcUrl: String): String? {
    val match = Regex("""^jdbc:postgresql://([^/:?]+)""").find(jdbcUrl) ?: return null
    return match.groupValues[1]
}

/** Извлекает hostname из Redis URL для security policy checks. */
private fun extractRedisHost(redisUrl: String): String? {
    return runCatching {
        java.net.URI(redisUrl).host
    }.getOrNull()
}

/** Проверяет, относится ли hostname к локальному безопасному окружению. */
private fun String.isLocalHostName(): Boolean {
    if (equals("localhost", ignoreCase = true)) return true
    if (equals("127.0.0.1")) return true
    if (equals("::1")) return true
    if (equals("postgres", ignoreCase = true)) return true
    if (equals("redis", ignoreCase = true)) return true
    return false
}
