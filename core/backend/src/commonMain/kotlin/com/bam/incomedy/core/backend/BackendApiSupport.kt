package com.bam.incomedy.core.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Общий JSON parser для backend API клиентов auth/session модулей. */
val backendJson: Json = Json { ignoreUnknownKeys = true }

/** Создает типовой Ktor client для backend JSON API. */
fun createBackendHttpClient(parser: Json = backendJson): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(parser)
        }
    }
}

/** Проверяет успешность backend-ответа и поднимает исключение с correlation id при ошибке. */
suspend fun ensureBackendSuccess(
    response: HttpResponse,
    parser: Json,
) {
    if (response.status.isSuccess()) {
        return
    }
    val failure = parseBackendFailure(
        response = response,
        parser = parser,
    )
    throw BackendStatusException(
        statusCode = response.status.value,
        requestId = failure.requestId,
        message = failure.message,
    )
}

/** Извлекает человекочитаемое сообщение об ошибке из backend-ответа. */
private suspend fun parseBackendFailure(
    response: HttpResponse,
    parser: Json,
): BackendFailure {
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
fun HttpRequestBuilder.bearer(accessToken: String) {
    header(HttpHeaders.Authorization, "Bearer $accessToken")
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
