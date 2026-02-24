package com.bam.incomedy.data.auth.backend

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

class TelegramBackendApi(
    private val baseUrl: String = AuthBackendConfig.baseUrl,
    private val parser: Json = Json { ignoreUnknownKeys = true },
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    },
) {
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

    suspend fun getSessionUser(accessToken: String): Result<SessionUser> {
        return runCatching {
            val response = httpClient
                .get("$baseUrl/api/v1/auth/session/me") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
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

    suspend fun logout(accessToken: String): Result<Unit> {
        return runCatching {
            val response = httpClient
                .post("$baseUrl/api/v1/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
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

    private suspend fun parseBackendError(response: HttpResponse): String {
        val body = response.bodyAsText()
        return runCatching {
            val error = parser.decodeFromString(BackendErrorResponse.serializer(), body)
            error.message ?: "Request failed with status ${response.status.value}"
        }.getOrElse {
            "Request failed with status ${response.status.value}"
        }
    }
}

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

data class TelegramBackendSession(
    val userId: String,
    val accessToken: String,
)

@Serializable
private data class TelegramBackendSessionResponse(
    @SerialName("access_token")
    val accessToken: String,
    val user: TelegramBackendUserResponse,
) {
    fun toSession(): TelegramBackendSession {
        return TelegramBackendSession(
            userId = user.id,
            accessToken = accessToken,
        )
    }
}

@Serializable
private data class TelegramBackendUserResponse(
    val id: String,
)

data class SessionUser(
    val id: String,
)

@Serializable
private data class SessionMeResponse(
    val user: SessionUserResponse,
)

@Serializable
private data class SessionUserResponse(
    val id: String,
) {
    fun toDomain(): SessionUser {
        return SessionUser(id = id)
    }
}

@Serializable
private data class BackendErrorResponse(
    val code: String? = null,
    val message: String? = null,
)

class BackendStatusException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)
