package com.bam.incomedy.server.config

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val telegram: TelegramConfig,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            return AppConfig(
                database = DatabaseConfig(
                    jdbcUrl = env.require("DB_URL"),
                    username = env.require("DB_USER"),
                    password = env.require("DB_PASSWORD"),
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
            )
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
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

private fun Map<String, String>.require(name: String): String {
    return this[name]?.takeIf { it.isNotBlank() }
        ?: error("Environment variable '$name' is required")
}

