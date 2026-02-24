package com.bam.incomedy.data.auth.providers

import com.bam.incomedy.data.auth.backend.TelegramBackendApi
import com.bam.incomedy.data.auth.backend.TelegramVerifyPayload
import com.bam.incomedy.feature.auth.domain.AuthLaunchRequest
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.AuthSession
import com.bam.incomedy.feature.auth.domain.SocialAuthProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class TelegramAuthProvider(
    private val botId: String,
    private val origin: String,
    private val redirectUri: String,
    private val backendApi: TelegramBackendApi,
) : SocialAuthProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override val type: AuthProviderType = AuthProviderType.TELEGRAM

    override suspend fun createLaunchRequest(state: String): Result<AuthLaunchRequest> {
        val url = buildUrl(
            base = "https://oauth.telegram.org/auth",
            params = mapOf(
                "bot_id" to botId,
                "origin" to origin,
                "return_to" to redirectUri,
                "request_access" to "write",
                "state" to state,
            ),
        )
        return Result.success(AuthLaunchRequest(type, state, url))
    }

    override suspend fun exchangeCode(code: String, state: String): Result<AuthSession> {
        val payload = parseTelegramPayload(callbackUrl = code)
            ?: return Result.failure(IllegalArgumentException("Invalid Telegram callback payload"))

        return backendApi.verifyTelegram(payload).map { session ->
            AuthSession(
                provider = type,
                userId = session.userId,
                accessToken = session.accessToken,
            )
        }
    }

    private fun parseTelegramPayload(callbackUrl: String): TelegramVerifyPayload? {
        val params = buildMap {
            putAll(parseKeyValuePart(callbackUrl.substringAfter('?', "").substringBefore('#')))
            putAll(parseKeyValuePart(callbackUrl.substringAfter('#', "")))
        }
        val expanded = decodeTgAuthResult(params["tgAuthResult"])?.let { decoded ->
            params + decoded
        } ?: params

        val id = expanded["id"]?.toLongOrNull() ?: return null
        val firstName = expanded["first_name"]?.takeIf { it.isNotBlank() } ?: return null
        val authDate = expanded["auth_date"]?.toLongOrNull() ?: return null
        val hash = expanded["hash"]?.takeIf { it.isNotBlank() } ?: return null

        return TelegramVerifyPayload(
            id = id,
            firstName = firstName,
            lastName = expanded["last_name"],
            username = expanded["username"],
            photoUrl = expanded["photo_url"],
            authDate = authDate,
            hash = hash,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeTgAuthResult(encoded: String?): Map<String, String>? {
        val raw = encoded?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val normalized = normalizeBase64(raw)
            val decodedJson = Base64.Default.decode(normalized).decodeToString()
            val payload = json.parseToJsonElement(decodedJson).jsonObject
            mapOf(
                "id" to payload["id"]?.toString()?.trim('"'),
                "first_name" to payload["first_name"]?.jsonPrimitive?.contentOrNull,
                "last_name" to payload["last_name"]?.jsonPrimitive?.contentOrNull,
                "username" to payload["username"]?.jsonPrimitive?.contentOrNull,
                "photo_url" to payload["photo_url"]?.jsonPrimitive?.contentOrNull,
                "auth_date" to payload["auth_date"]?.toString()?.trim('"'),
                "hash" to payload["hash"]?.jsonPrimitive?.contentOrNull,
            ).mapValues { it.value?.trim() ?: "" }
                .filterValues { it.isNotBlank() }
        }.getOrNull()
    }

    private fun normalizeBase64(value: String): String {
        val replaced = value.replace('-', '+').replace('_', '/')
        val padding = (4 - (replaced.length % 4)) % 4
        return replaced + "=".repeat(padding)
    }

    private fun parseKeyValuePart(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value
            .split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                pair.substring(0, index) to decodePercent(pair.substring(index + 1))
            }
            .toMap()
    }

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
}
