package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.config.VkIdConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Abstraction over VK ID HTTP endpoints used by the backend auth flow.
 */
interface VkIdGateway {
    /** Builds a provider authorize URL for the selected VK client and redirect target. */
    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String

    /** Exchanges a VK authorization code into provider tokens. */
    fun exchangeAuthorizationCode(
        clientId: String,
        redirectUri: String,
        code: String,
        state: String,
        deviceId: String,
        codeVerifier: String,
    ): VkIdTokenResponse

    /** Loads VK user info for the selected VK client id and access token. */
    fun loadUserInfo(clientId: String, accessToken: String): VkIdUserInfoResponse
}

/**
 * Production VK ID gateway backed by direct HTTP calls to VK ID endpoints.
 */
class VkIdClient(
    private val config: VkIdConfig,
    private val parser: Json = Json { ignoreUnknownKeys = true },
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : VkIdGateway {
    override fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String {
        return buildUrl(
            base = "https://id.vk.ru/authorize",
            params = linkedMapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
                "scope" to config.scope,
            ),
        )
    }

    override fun exchangeAuthorizationCode(
        clientId: String,
        redirectUri: String,
        code: String,
        state: String,
        deviceId: String,
        codeVerifier: String,
    ): VkIdTokenResponse {
        val response = formPost(
            url = "https://id.vk.ru/oauth2/auth",
            body = linkedMapOf(
                "grant_type" to "authorization_code",
                "code_verifier" to codeVerifier,
                "redirect_uri" to redirectUri,
                "code" to code,
                "client_id" to clientId,
                "device_id" to deviceId,
                "state" to state,
            ),
        )
        if (response.statusCode() !in 200..299) {
            throw VkIdCodeExchangeException("VK ID token exchange failed with status ${response.statusCode()}")
        }
        val parsedResponse = parser.decodeFromString(VkIdTokenResponse.serializer(), response.body())
        if (parsedResponse.state != state) {
            throw VkIdCodeExchangeException("VK ID state mismatch in token exchange response")
        }
        return parsedResponse
    }

    override fun loadUserInfo(clientId: String, accessToken: String): VkIdUserInfoResponse {
        val response = formPost(
            url = "https://id.vk.ru/oauth2/user_info",
            body = linkedMapOf(
                "client_id" to clientId,
                "access_token" to accessToken,
            ),
        )
        if (response.statusCode() !in 200..299) {
            throw VkIdUserInfoException("VK ID user info request failed with status ${response.statusCode()}")
        }
        return parser.decodeFromString(VkIdUserInfoResponse.serializer(), response.body())
    }

    private fun formPost(
        url: String,
        body: Map<String, String>,
    ): HttpResponse<String> {
        val encodedBody = body.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodedBody))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        return "$base?$query"
    }

    private fun String.encode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
}

@Serializable
data class VkIdTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("user_id")
    @Serializable(with = VkFlexibleNullableStringSerializer::class)
    val userId: String? = null,
    @SerialName("id_token")
    val idToken: String? = null,
    val state: String? = null,
    val scope: String? = null,
)

@Serializable
data class VkIdUserInfoResponse(
    val user: VkIdUserInfo,
)

@Serializable
data class VkIdUserInfo(
    @SerialName("user_id")
    @Serializable(with = VkFlexibleStringSerializer::class)
    val userId: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

class VkIdCodeExchangeException(
    override val message: String,
) : IllegalStateException(message)

class VkIdUserInfoException(
    override val message: String,
) : IllegalStateException(message)

/**
 * Декодирует VK поля, которые в разных ответах могут приходить как строка или число.
 *
 * В реальных ответах VK `user_id` уже вернулся числом, хотя приложение хранит внешние identity ids
 * как строки. Сериализатор приводит оба формата к единому строковому виду.
 */
object VkFlexibleNullableStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VkFlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
            ?: error("VK flexible string must be a primitive value")
        return primitive.content
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeString(value)
    }
}

/** Требует непустое примитивное значение и приводит numeric/string VK `user_id` к строке. */
object VkFlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VkFlexibleNonNullString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        return VkFlexibleNullableStringSerializer.deserialize(decoder)
            ?: error("VK flexible string is missing")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
