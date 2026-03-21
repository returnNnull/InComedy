package com.bam.incomedy.server.config

/**
 * Корневой runtime-конфиг backend-приложения.
 *
 * @property database Настройки подключения к PostgreSQL.
 * @property jwt Настройки внутренних JWT-сессий.
 * @property yooKassa Настройки активного PSP checkout-а, если интеграция включена.
 * @property redis Настройки Redis rate limiter-а, если он включен.
 */
data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val vkId: VkIdConfig?,
    val iosAssociatedDomains: IosAssociatedDomainsConfig?,
    val yooKassa: YooKassaConfig?,
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
                yooKassa = env.yooKassaConfig(),
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

/**
 * Конфиг явно включенной YooKassa checkout-интеграции.
 *
 * @property shopId Идентификатор магазина YooKassa.
 * @property secretKey Секретный ключ API YooKassa.
 * @property returnUrl Merchant-controlled URL возврата пользователя после редиректа из PSP.
 * @property apiBaseUrl Базовый URL REST API YooKassa.
 * @property capture Признак автоматического capture платежа после авторизации.
 */
data class YooKassaConfig(
    val shopId: String,
    val secretKey: String,
    val returnUrl: String,
    val apiBaseUrl: String,
    val capture: Boolean,
)

/**
 * Конфиг интеграции с VK ID.
 *
 * @property clientId Browser/public client id.
 * @property redirectUri Канонический redirect URI для browser flow.
 * @property androidClientId Отдельный Android client id для SDK path, если он включен.
 * @property androidRedirectUri Redirect URI Android SDK path.
 * @property scope OAuth scope VK ID.
 * @property stateSecret Секрет подписи server-side state.
 * @property stateTtlSeconds TTL состояния VK auth flow.
 */
data class VkIdConfig(
    val clientId: String,
    val redirectUri: String,
    val androidClientId: String? = null,
    val androidRedirectUri: String? = null,
    val scope: String,
    val stateSecret: String,
    val stateTtlSeconds: Long,
)

/**
 * Конфиг Apple App Site Association для iOS auth-return surface.
 *
 * @property appIds Список разрешенных `TEAMID.bundleId`.
 * @property paths Пути, включаемые в AASA-ответ.
 */
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
    val androidClientId = this["VK_ID_ANDROID_CLIENT_ID"]?.trim().takeUnless { it.isNullOrBlank() }
    val androidRedirectUri = this["VK_ID_ANDROID_REDIRECT_URI"]?.trim().takeUnless { it.isNullOrBlank() }
    require(!clientId.isNullOrBlank()) { "Environment variable 'VK_ID_CLIENT_ID' is required when VK ID auth is enabled" }
    require(!redirectUri.isNullOrBlank()) { "Environment variable 'VK_ID_REDIRECT_URI' is required when VK ID auth is enabled" }
    require((androidClientId == null) == (androidRedirectUri == null)) {
        "Environment variables 'VK_ID_ANDROID_CLIENT_ID' and 'VK_ID_ANDROID_REDIRECT_URI' must be configured together"
    }
    return VkIdConfig(
        clientId = clientId,
        redirectUri = redirectUri,
        androidClientId = androidClientId,
        androidRedirectUri = androidRedirectUri,
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

/**
 * Загружает optional YooKassa config только при явном `YOOKASSA_ENABLED=true`.
 *
 * Это позволяет держать реализацию PSP в кодовой базе, но не активировать ее молча по одному
 * лишь присутствию env-переменных. Пока флаг не включен, сервер игнорирует частично заполненный
 * YooKassa-конфиг и продолжает запускаться без checkout provider-а.
 */
private fun Map<String, String>.yooKassaConfig(): YooKassaConfig? {
    val enabled = this["YOOKASSA_ENABLED"]?.toBooleanStrictOrNull() ?: false
    if (!enabled) {
        return null
    }
    val shopId = this["YOOKASSA_SHOP_ID"]?.trim()
    val secretKey = this["YOOKASSA_SECRET_KEY"]?.trim()
    val returnUrl = this["YOOKASSA_RETURN_URL"]?.trim()
    require(!shopId.isNullOrBlank()) {
        "Environment variable 'YOOKASSA_SHOP_ID' is required when YooKassa checkout is enabled"
    }
    require(!secretKey.isNullOrBlank()) {
        "Environment variable 'YOOKASSA_SECRET_KEY' is required when YooKassa checkout is enabled"
    }
    require(!returnUrl.isNullOrBlank()) {
        "Environment variable 'YOOKASSA_RETURN_URL' is required when YooKassa checkout is enabled"
    }
    val parsedReturnUrl = runCatching { java.net.URI(returnUrl) }.getOrNull()
    require(parsedReturnUrl?.scheme in setOf("http", "https")) {
        "Environment variable 'YOOKASSA_RETURN_URL' must be a valid http/https URL"
    }
    return YooKassaConfig(
        shopId = shopId,
        secretKey = secretKey,
        returnUrl = returnUrl,
        apiBaseUrl = this["YOOKASSA_API_BASE_URL"]?.trim().takeUnless { it.isNullOrBlank() } ?: "https://api.yookassa.ru/v3",
        capture = this["YOOKASSA_CAPTURE"]?.toBooleanStrictOrNull() ?: true,
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
