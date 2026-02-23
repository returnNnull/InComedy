package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object TelegramAuthRoutes {
    fun register(route: Route, authService: TelegramAuthService) {
        route.post("/api/v1/auth/telegram/verify") {
            val request = runCatching { call.receive<TelegramVerifyRequest>() }.getOrElse {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("bad_request", "Invalid Telegram auth payload"),
                )
                return@post
            }

            val result = authService.verifyAndCreateSession(request)
            result.fold(
                onSuccess = { auth ->
                    call.respond(
                        HttpStatusCode.OK,
                        TelegramAuthResponse(
                            accessToken = auth.accessToken,
                            refreshToken = auth.refreshToken,
                            expiresInSeconds = auth.expiresInSeconds,
                            user = TelegramAuthUserResponse(
                                id = auth.user.id,
                                displayName = auth.user.displayName,
                                username = auth.user.username,
                                photoUrl = auth.user.photoUrl,
                            ),
                        ),
                    )
                },
                onFailure = { error ->
                    val status = if (error.message?.contains("hash", ignoreCase = true) == true ||
                        error.message?.contains("expired", ignoreCase = true) == true
                    ) {
                        HttpStatusCode.Unauthorized
                    } else {
                        HttpStatusCode.BadRequest
                    }
                    call.respond(
                        status,
                        ErrorResponse("telegram_auth_failed", error.message ?: "Telegram auth failed"),
                    )
                },
            )
        }
    }
}

@Serializable
data class TelegramAuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresInSeconds: Long,
    val user: TelegramAuthUserResponse,
)

@Serializable
data class TelegramAuthUserResponse(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val username: String? = null,
    @SerialName("photo_url")
    val photoUrl: String? = null,
)
