package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.auth.AuthLaunchRequest
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.domain.auth.AuthSession
import com.bam.incomedy.domain.auth.AuthorizedUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент backend API, отвечающий только за auth и lifecycle внутренней сессии.
 *
 * Клиент больше не знает о ролях и organizer workspace context: эти post-auth обязанности
 * вынесены в отдельный session-модуль, чтобы auth-слой удерживал только вход, refresh,
 * logout и provider-specific transport.
 *
 * @property baseUrl Базовый URL текущего backend API.
 * @property parser JSON-парсер для fallback-разбора ошибок.
 * @property httpClient Настроенный Ktor-клиент для auth/session запросов.
 */
class AuthBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) : TelegramAuthGateway {

    /** Выполняет credential registration и возвращает внутреннюю сессию. */
    suspend fun registerWithPassword(login: String, password: String): Result<AuthSession> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(CredentialAuthRequest(login = login, password = password))
                }
            ensureBackendSuccess(response, parser)
            response.body<TelegramBackendSessionResponse>().toAuthSession()
        }
    }

    /** Выполняет credential login и возвращает внутреннюю сессию. */
    suspend fun signInWithPassword(login: String, password: String): Result<AuthSession> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody(CredentialAuthRequest(login = login, password = password))
                }
            ensureBackendSuccess(response, parser)
            response.body<TelegramBackendSessionResponse>().toAuthSession()
        }
    }

    /** Запрашивает VK launch request для browser or SDK entry flow. */
    suspend fun startVkAuth(): Result<AuthLaunchRequest> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/auth/vk/start")
            ensureBackendSuccess(response, parser)
            response.body<VkAuthLaunchResponse>().toDomain()
        }
    }

    /** Завершает VK login по raw callback URL, который парсится в backend verify payload. */
    suspend fun verifyVk(callbackUrl: String): Result<AuthSession> {
        return runCatching {
            val payload = parseVkCallbackPayload(callbackUrl)
                ?: error("Invalid VK auth callback payload")
            val response = httpClient
                .post("$baseUrl/api/v1/auth/vk/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            ensureBackendSuccess(response, parser)
            response.body<TelegramBackendSessionResponse>().toAuthSession()
        }
    }

    /** Запрашивает backend launch URL для официального Telegram browser auth flow. */
    override suspend fun startTelegramAuth(): Result<TelegramAuthLaunch> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/auth/telegram/start")
            ensureBackendSuccess(response, parser)
            response.body<TelegramAuthLaunchResponse>().toDomain()
        }
    }

    /** Отправляет Telegram verify payload и возвращает серверную сессию. */
    override suspend fun verifyTelegram(payload: TelegramVerifyPayload): Result<TelegramBackendSession> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/telegram/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            ensureBackendSuccess(response, parser)
            response
                .body<TelegramBackendSessionResponse>()
                .toSession()
        }
    }

    /** Загружает профиль текущей сессии по access token. */
    suspend fun getSessionUser(accessToken: String): Result<SessionUser> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/auth/session/me") {
                    bearer(accessToken)
                }
            ensureBackendSuccess(response, parser)
            response.body<SessionMeResponse>().let { session ->
                session.user.toDomain(
                    provider = session.provider.toAuthProviderType(),
                )
            }
        }
    }

    /** Завершает текущую backend-сессию по access token. */
    suspend fun logout(accessToken: String): Result<Unit> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/logout") {
                    bearer(accessToken)
                }
            ensureBackendSuccess(response, parser)
            Unit
        }
    }

    /** Обновляет access token через refresh token и возвращает обновленную сессию. */
    suspend fun refreshSession(refreshToken: String): Result<RefreshedBackendSession> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken = refreshToken))
                }
            ensureBackendSuccess(response, parser)
            response.body<RefreshResponse>().toDomain()
        }
    }
}

/**
 * Разбирает raw VK callback URL из mobile/browser flow в verify payload для backend.
 *
 * Парсер принимает параметры и из query, и из fragment, затем percent-decode-ит значения,
 * чтобы backend увидел ту же логическую нагрузку, которую bridge сохранил в callback URL.
 */
internal fun parseVkCallbackPayload(callbackUrl: String): VkVerifyPayload? {
    val query = callbackUrl.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    val fragment = callbackUrl.substringAfter('#', missingDelimiterValue = "")
    val params = buildMap {
        putAll(parseQueryLike(query))
        putAll(parseQueryLike(fragment))
    }
    val code = params["code"]?.takeIf { it.isNotBlank() } ?: return null
    val state = params["state"]?.takeIf { it.isNotBlank() } ?: return null
    val deviceId = params["device_id"]?.takeIf { it.isNotBlank() } ?: return null
    return VkVerifyPayload(
        code = code,
        state = state,
        deviceId = deviceId,
        codeVerifier = params["code_verifier"]?.takeIf { it.isNotBlank() },
        clientSource = params["client_source"]?.takeIf { it.isNotBlank() } ?: "browser_bridge",
    )
}

/** Разбирает один query-подобный сегмент в декодированные пары ключ/значение. */
private fun parseQueryLike(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    return value.split('&')
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val key = pair.substring(0, index)
            val encodedValue = pair.substring(index + 1)
            key to decodePercent(encodedValue)
        }
        .toMap()
}

/** Декодирует percent-escaped callback значения без platform-specific helper-ов. */
private fun decodePercent(value: String): String {
    val sb = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3)
            val decoded = hex.toIntOrNull(16)
            if (decoded != null) {
                sb.append(decoded.toChar())
                index += 3
                continue
            }
        }
        if (ch == '+') sb.append(' ') else sb.append(ch)
        index++
    }
    return sb.toString()
}

/**
 * Launch-конфигурация Telegram auth, полученная от backend-а.
 *
 * @property authUrl First-party InComedy launch URL для открытия во внешнем браузере.
 * @property state Серверно выданный state текущей auth-попытки.
 */
data class TelegramAuthLaunch(
    val authUrl: String,
    val state: String,
)

/**
 * Payload завершения Telegram auth после возврата из callback bridge.
 *
 * @property code Telegram authorization code из callback URL.
 * @property state Серверно выданный state текущей auth-попытки.
 */
@Serializable
data class TelegramVerifyPayload(
    val code: String,
    val state: String,
)

/**
 * Серверное представление только что подтвержденной Telegram-сессии.
 *
 * @property userId Внутренний идентификатор пользователя.
 * @property accessToken Выданный access token.
 * @property refreshToken Выданный refresh token, если backend его вернул.
 * @property user Обогащенный профиль пользователя.
 */
data class TelegramBackendSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String?,
    val user: AuthorizedUser,
)

/**
 * Серверное представление сессии после refresh.
 *
 * @property userId Внутренний идентификатор пользователя.
 * @property accessToken Новый access token.
 * @property refreshToken Новый refresh token.
 * @property user Обновленный профиль пользователя.
 */
data class RefreshedBackendSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val user: AuthorizedUser,
)

/**
 * DTO backend-ответа на успешную auth/verify операцию.
 *
 * @property accessToken Выданный access token.
 * @property refreshToken Выданный refresh token.
 * @property user Пользователь из backend-ответа.
 */
@Serializable
private data class TelegramBackendSessionResponse(
    val provider: String? = null,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val user: TelegramBackendUserResponse,
) {
    /** Преобразует verify-ответ в доменную модель сессии. */
    fun toSession(): TelegramBackendSession {
        return TelegramBackendSession(
            provider = provider.toAuthProviderType(),
            userId = user.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toDomain(),
        )
    }

    /** Преобразует verify-ответ в общую auth-сессию приложения. */
    fun toAuthSession(): AuthSession {
        return AuthSession(
            provider = provider.toAuthProviderType(),
            userId = user.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toDomain(),
        )
    }
}

/**
 * DTO backend-ответа на успешный Telegram auth start.
 *
 * @property authUrl First-party InComedy launch URL.
 * @property state Серверно выданный state.
 */
@Serializable
private data class TelegramAuthLaunchResponse(
    @SerialName("auth_url")
    val authUrl: String,
    val state: String,
) {
    /** Преобразует backend launch response в доменную launch-модель. */
    fun toDomain(): TelegramAuthLaunch {
        return TelegramAuthLaunch(
            authUrl = authUrl,
            state = state,
        )
    }
}

/** DTO credential auth-запроса. */
@Serializable
private data class CredentialAuthRequest(
    val login: String,
    val password: String,
)

/** DTO backend-ответа на VK auth start. */
@Serializable
private data class VkAuthLaunchResponse(
    @SerialName("auth_url")
    val authUrl: String,
    val state: String,
) {
    /** Преобразует VK launch response в доменный auth launch request. */
    fun toDomain(): AuthLaunchRequest {
        return AuthLaunchRequest(
            provider = AuthProviderType.VK,
            state = state,
            url = authUrl,
        )
    }
}

/** DTO backend verify payload для VK auth exchange. */
@Serializable
internal data class VkVerifyPayload(
    val code: String,
    val state: String,
    @SerialName("device_id")
    val deviceId: String,
    /** PKCE verifier нужен только Android SDK flow и не приходит из browser/public callback path. */
    @SerialName("code_verifier")
    val codeVerifier: String? = null,
    @SerialName("client_source")
    val clientSource: String? = null,
)

/**
 * DTO refresh-запроса.
 *
 * @property refreshToken Refresh token, который нужно обменять на новую сессию.
 */
@Serializable
private data class RefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String,
)

/**
 * DTO backend-ответа на refresh.
 *
 * @property accessToken Новый access token.
 * @property refreshToken Новый refresh token.
 * @property user Профиль пользователя после refresh.
 */
@Serializable
private data class RefreshResponse(
    val provider: String? = null,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    val user: TelegramBackendUserResponse,
) {
    /** Преобразует refresh-ответ в доменную модель обновленной сессии. */
    fun toDomain(): RefreshedBackendSession {
        return RefreshedBackendSession(
            provider = provider.toAuthProviderType(),
            userId = user.id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user.toDomain(),
        )
    }
}

/**
 * DTO пользователя, который приходит в auth/session backend-ответах.
 *
 * @property id Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля.
 * @property photoUrl URL фотографии профиля.
 * @property roles Доступные роли пользователя.
 * @property activeRole Активная роль пользователя.
 * @property linkedProviders Привязанные способы входа.
 */
@Serializable
private data class TelegramBackendUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
) {
    /** Преобразует DTO пользователя в доменную модель авторизованного пользователя. */
    fun toDomain(): AuthorizedUser {
        return AuthorizedUser(
            id = id,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }
}

/**
 * Профиль пользователя, который приходит из `session/me`.
 *
 * @property id Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля.
 * @property photoUrl URL фотографии профиля.
 * @property roles Доступные роли пользователя.
 * @property activeRole Активная роль пользователя.
 * @property linkedProviders Привязанные способы входа.
 */
data class SessionUser(
    val provider: AuthProviderType,
    val id: String,
    val displayName: String,
    val username: String? = null,
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    val activeRole: String? = null,
    val linkedProviders: List<String> = emptyList(),
)

/**
 * DTO ответа `session/me`.
 *
 * @property user DTO текущего пользователя.
 */
@Serializable
private data class SessionMeResponse(
    val provider: String? = null,
    val user: SessionUserResponse,
)

/**
 * DTO пользователя в ответе `session/me`.
 *
 * @property id Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля.
 * @property photoUrl URL фотографии профиля.
 * @property roles Доступные роли пользователя.
 * @property activeRole Активная роль пользователя.
 * @property linkedProviders Привязанные способы входа.
 */
@Serializable
private data class SessionUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
) {
    /** Преобразует DTO пользователя в доменный профиль текущей сессии. */
    fun toDomain(provider: AuthProviderType): SessionUser {
        return SessionUser(
            provider = provider,
            id = id,
            displayName = displayName,
            username = username,
            photoUrl = photoUrl,
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }
}

/** Нормализует wire-имя backend provider-а в общий `AuthProviderType`. */
private fun String?.toAuthProviderType(): AuthProviderType {
    return when (this?.trim()?.lowercase()) {
        "password" -> AuthProviderType.PASSWORD
        "phone" -> AuthProviderType.PHONE
        "google" -> AuthProviderType.GOOGLE
        "telegram" -> AuthProviderType.TELEGRAM
        "vk" -> AuthProviderType.VK
        else -> AuthProviderType.PASSWORD
    }
}
