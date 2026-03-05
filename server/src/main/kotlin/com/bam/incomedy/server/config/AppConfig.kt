package com.bam.incomedy.server.config

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val telegram: TelegramConfig,
    val redis: RedisConfig?,
) {
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
                telegram = TelegramConfig(
                    botToken = env.require("TELEGRAM_BOT_TOKEN"),
                    maxAuthAgeSeconds = env["TELEGRAM_AUTH_MAX_AGE_SECONDS"]?.toLongOrNull() ?: 86400L,
                ),
                redis = redisConfig,
            )
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val sslMode: String,
    val allowInsecure: Boolean,
)

data class JwtConfig(
    val issuer: String,
    val secret: String,
    val accessTtlSeconds: Long,
    val refreshTtlSeconds: Long,
)

data class TelegramConfig(
    val botToken: String,
    val maxAuthAgeSeconds: Long,
)

data class RedisConfig(
    val url: String,
    val allowInsecure: Boolean,
)

private fun Map<String, String>.require(name: String): String {
    return this[name]?.takeIf { it.isNotBlank() }
        ?: error("Environment variable '$name' is required")
}

private fun withDbSslMode(jdbcUrl: String, sslMode: String): String {
    if ("sslmode=" in jdbcUrl.lowercase()) return jdbcUrl
    val delimiter = if ('?' in jdbcUrl) "&" else "?"
    return "$jdbcUrl${delimiter}sslmode=$sslMode"
}

private fun extractPostgresHost(jdbcUrl: String): String? {
    val match = Regex("""^jdbc:postgresql://([^/:?]+)""").find(jdbcUrl) ?: return null
    return match.groupValues[1]
}

private fun extractRedisHost(redisUrl: String): String? {
    return runCatching {
        java.net.URI(redisUrl).host
    }.getOrNull()
}

private fun String.isLocalHostName(): Boolean {
    if (equals("localhost", ignoreCase = true)) return true
    if (equals("127.0.0.1")) return true
    if (equals("::1")) return true
    if (equals("postgres", ignoreCase = true)) return true
    if (equals("redis", ignoreCase = true)) return true
    return false
}
