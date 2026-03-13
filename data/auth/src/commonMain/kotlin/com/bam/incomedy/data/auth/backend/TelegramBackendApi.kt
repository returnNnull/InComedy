package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace
import com.bam.incomedy.feature.auth.domain.SessionRoleContext
import com.bam.incomedy.feature.auth.domain.AuthorizedUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент backend API авторизации, профиля, ролей и рабочих пространств.
 *
 * @property baseUrl Базовый URL текущего backend API.
 * @property parser JSON-парсер для fallback-разбора ошибок.
 * @property httpClient Настроенный Ktor-клиент для всех auth/session запросов.
 */
class TelegramBackendApi(
    private val baseUrl: String = AuthBackendConfig.baseUrl,
    private val parser: Json = Json { ignoreUnknownKeys = true },
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) : TelegramAuthGateway {
    /** Запрашивает backend launch URL для официального Telegram browser auth flow. */
    override suspend fun startTelegramAuth(): Result<TelegramAuthLaunch> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/auth/telegram/start")
            ensureSuccess(response)
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
            ensureSuccess(response)
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
            ensureSuccess(response)
            response
                .body<SessionMeResponse>()
                .user
                .toDomain()
        }
    }

    /** Загружает роли, активную роль и привязанные способы входа текущего пользователя. */
    suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/me/roles") {
                    bearer(accessToken)
                }
            ensureSuccess(response)
            response.body<MeRolesResponse>().toDomain()
        }
    }

    /** Переключает активную роль текущего пользователя. */
    suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/me/active-role") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(SetActiveRoleRequest(role = role))
                }
            ensureSuccess(response)
            response.body<MeRolesResponse>().toDomain()
        }
    }

    /** Возвращает список рабочих пространств, доступных текущей сессии. */
    suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/workspaces") {
                    bearer(accessToken)
                }
            ensureSuccess(response)
            response.body<WorkspaceListResponse>().workspaces.map(WorkspaceResponse::toDomain)
        }
    }

    /** Создает рабочее пространство организатора и возвращает его серверное представление. */
    suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String? = null,
    ): Result<OrganizerWorkspace> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/workspaces") {
                    bearer(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(CreateWorkspaceRequest(name = name, slug = slug))
                }
            ensureSuccess(response)
            response.body<WorkspaceResponse>().toDomain()
        }
    }

    /** Завершает текущую backend-сессию по access token. */
    suspend fun logout(accessToken: String): Result<Unit> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/logout") {
                    bearer(accessToken)
                }
            ensureSuccess(response)
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
            ensureSuccess(response)
            response.body<RefreshResponse>().toDomain()
        }
    }

    /** Проверяет успешность backend-ответа и поднимает исключение с correlation id при ошибке. */
    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) {
            return
        }
        val failure = parseBackendFailure(response)
        throw BackendStatusException(
            statusCode = response.status.value,
            requestId = failure.requestId,
            message = failure.message,
        )
    }

    /** Извлекает человекочитаемое сообщение об ошибке из backend-ответа. */
    private suspend fun parseBackendFailure(response: HttpResponse): BackendFailure {
        val body = response.bodyAsText()
        val message = runCatching {
            val error = parser.decodeFromString(BackendErrorResponse.serializer(), body)
            error.message ?: "Request failed with status ${response.status.value}"
        }.getOrElse {
            "Request failed with status ${response.status.value}"
        }
        return BackendFailure(
            message = message,
            requestId = response.headers["X-Request-ID"],
        )
    }

    /** Добавляет bearer token в Authorization-заголовок запроса. */
    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
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
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val user: AuthorizedUser,
)

/**
 * DTO backend-ответа на успешную Telegram verify операцию.
 *
 * @property accessToken Выданный access token.
 * @property refreshToken Выданный refresh token.
 * @property user Пользователь из backend-ответа.
 */
@Serializable
private data class TelegramBackendSessionResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val user: TelegramBackendUserResponse,
) {
    /** Преобразует verify-ответ в доменную модель сессии. */
    fun toSession(): TelegramBackendSession {
        return TelegramBackendSession(
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
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    val user: TelegramBackendUserResponse,
) {
    /** Преобразует refresh-ответ в доменную модель обновленной сессии. */
    fun toDomain(): RefreshedBackendSession {
        return RefreshedBackendSession(
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
    fun toDomain(): SessionUser {
        return SessionUser(
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
 * DTO ответа с role context пользователя.
 *
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Привязанные способы входа.
 */
@Serializable
private data class MeRolesResponse(
    val roles: List<String>,
    @SerialName("active_role")
    val activeRole: String? = null,
    @SerialName("linked_providers")
    val linkedProviders: List<String> = emptyList(),
) {
    /** Преобразует role-context ответ в доменную модель. */
    fun toDomain(): SessionRoleContext {
        return SessionRoleContext(
            roles = roles,
            activeRole = activeRole,
            linkedProviders = linkedProviders,
        )
    }
}

/**
 * DTO запроса смены активной роли.
 *
 * @property role Код роли, которую нужно сделать активной.
 */
@Serializable
private data class SetActiveRoleRequest(
    val role: String,
)

/**
 * DTO списка рабочих пространств.
 *
 * @property workspaces Рабочие пространства, доступные пользователю.
 */
@Serializable
private data class WorkspaceListResponse(
    val workspaces: List<WorkspaceResponse>,
)

/**
 * DTO запроса на создание рабочего пространства.
 *
 * @property name Название рабочего пространства.
 * @property slug Необязательный публичный slug рабочего пространства.
 */
@Serializable
private data class CreateWorkspaceRequest(
    val name: String,
    val slug: String? = null,
)

/**
 * DTO рабочего пространства в backend-ответах.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль доступа пользователя внутри рабочего пространства.
 */
@Serializable
private data class WorkspaceResponse(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    @SerialName("permission_role")
    val permissionRole: String,
) {
    /** Преобразует DTO рабочего пространства в доменную модель. */
    fun toDomain(): OrganizerWorkspace {
        return OrganizerWorkspace(
            id = id,
            name = name,
            slug = slug,
            status = status,
            permissionRole = permissionRole,
        )
    }
}

/**
 * DTO backend failure с trace identifier для корреляции device/server логов.
 *
 * @property message Безопасное сообщение backend-ошибки.
 * @property requestId Echoed `X-Request-ID`, если сервер его вернул.
 */
private data class BackendFailure(
    val message: String,
    val requestId: String? = null,
)

/**
 * DTO стандартной backend-ошибки.
 *
 * @property code Машинный код ошибки.
 * @property message Человекочитаемое сообщение backend-а.
 */
@Serializable
private data class BackendErrorResponse(
    val code: String? = null,
    val message: String? = null,
)

/**
 * Исключение backend API с сохранением HTTP-статуса.
 *
 * @property statusCode HTTP-статус ответа.
 * @property requestId Correlation id backend-ответа для поиска серверной диагностики.
 */
class BackendStatusException(
    val statusCode: Int,
    val requestId: String? = null,
    message: String,
) : Exception(
    requestId?.takeIf { it.isNotBlank() }?.let { "$message (requestId=$it)" } ?: message,
)
