package com.bam.incomedy.server.auth.telegram

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/**
 * Минимальный transport-слой для Telegram OIDC HTTP-запросов.
 */
interface TelegramOidcTransport {
    /** Выполняет GET-запрос и возвращает raw response body или исключение. */
    fun get(url: String): Result<String>

    /** Выполняет POST form-запрос и возвращает HTTP-статус с raw response body. */
    fun postForm(
        url: String,
        formFields: Map<String, String>,
        basicAuthUsername: String? = null,
        basicAuthPassword: String? = null,
    ): Result<TelegramHttpResponse>
}

/**
 * JDK-реализация transport-слоя Telegram OIDC на базе `java.net.http.HttpClient`.
 *
 * @property httpClient Настроенный HTTP-клиент для Telegram discovery/token/JWKS запросов.
 */
class JdkTelegramOidcTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : TelegramOidcTransport {
    /** Выполняет GET-запрос к Telegram OIDC endpoint. */
    override fun get(url: String): Result<String> {
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
        }
    }

    /** Выполняет POST form-запрос к Telegram OIDC token endpoint. */
    override fun postForm(
        url: String,
        formFields: Map<String, String>,
        basicAuthUsername: String?,
        basicAuthPassword: String?,
    ): Result<TelegramHttpResponse> {
        return runCatching {
            val encodedForm = formFields.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
            val requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodedForm))
            if (!basicAuthUsername.isNullOrBlank() && !basicAuthPassword.isNullOrBlank()) {
                requestBuilder.header(
                    "Authorization",
                    basicAuthHeader(basicAuthUsername, basicAuthPassword),
                )
            }
            val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            TelegramHttpResponse(
                statusCode = response.statusCode(),
                body = response.body(),
            )
        }
    }

    /** Кодирует form field согласно application/x-www-form-urlencoded. */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
    }

    /** Строит Basic authorization header для token endpoint. */
    private fun basicAuthHeader(username: String, password: String): String {
        val raw = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}

/**
 * Нормализованный HTTP-ответ transport-слоя Telegram OIDC.
 *
 * @property statusCode HTTP-статус ответа.
 * @property body Тело ответа в виде raw string.
 */
data class TelegramHttpResponse(
    val statusCode: Int,
    val body: String,
)
