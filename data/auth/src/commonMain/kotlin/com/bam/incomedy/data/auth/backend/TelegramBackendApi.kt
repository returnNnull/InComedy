package com.bam.incomedy.data.auth.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TelegramBackendApi(
    private val baseUrl: String = AuthBackendConfig.baseUrl,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    },
) {
    suspend fun verifyTelegram(payload: TelegramVerifyPayload): Result<TelegramBackendSession> {
        return runCatching {
            httpClient
                .post("$baseUrl/api/v1/auth/telegram/verify") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                .body<TelegramBackendSessionResponse>()
                .toSession()
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

