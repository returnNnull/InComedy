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
) {
    /** Отправляет Telegram verify payload и возвращает серверную сессию. */
    suspend fun verifyTelegram(payload: TelegramVerifyPayload): Result<TelegramBackendSession> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/telegram/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
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
            if (!response.status.isSuccess()) {
                throw BackendStatusException(
                    statusCode = response.status.value,
                    message = parseBackendError(response),
                )
            }
            response.body<RefreshResponse>().toDomain()
        }
    }

    /** Извлекает человекочитаемое сообщение об ошибке из backend-ответа. */
    private suspend fun parseBackendError(response: HttpResponse): String {
        val body = response.bodyAsText()
        return runCatching {
            val error = parser.decodeFromString(BackendErrorResponse.serializer(), body)
            error.message ?: "Request failed with status ${response.status.value}"
        }.getOrElse {
            "Request failed with status ${response.status.value}"
        }
    }

    /** Добавляет bearer token в Authorization-заголовок запроса. */
    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
}

/**
 * Payload подтверждения входа через Telegram Login Widget.
 *
 * @property id Telegram identifier пользователя.
 * @property firstName Имя пользователя из Telegram.
 * @property lastName Фамилия пользователя из Telegram.
 * @property username Username пользователя из Telegram.
 * @property photoUrl URL фотографии профиля из Telegram.
 * @property authDate Время авторизации в формате Unix timestamp.
 * @property hash Контрольная подпись Telegram.
 */
@Serializable
data class TelegramVerifyPayload(
    val id: Long,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String? = null,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("auth_date")
    val authDate: Long,
    val hash: String,
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
 * @property message Сообщение backend-ошибки.
 */
class BackendStatusException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
