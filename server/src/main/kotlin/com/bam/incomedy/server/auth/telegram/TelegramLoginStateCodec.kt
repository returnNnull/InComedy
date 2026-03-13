package com.bam.incomedy.server.auth.telegram

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Выпускает и проверяет серверно подписанный Telegram login `state`, содержащий PKCE verifier.
 *
 * @property redirectUri Официальный redirect URI Telegram login.
 * @property secret Секрет подписи `state`.
 * @property ttlSeconds Максимальное время жизни одного Telegram login `state`.
 * @property nowProvider Источник текущего времени для проверок и тестов.
 */
class TelegramLoginStateCodec(
    private val redirectUri: String,
    secret: String,
    private val ttlSeconds: Long,
    private val nowProvider: () -> Instant = Instant::now,
) {
    /** Бинарное представление секрета подписи `state`. */
    private val secretBytes = secret.toByteArray(StandardCharsets.UTF_8)

    /** JSON-парсер для сериализации полезной нагрузки `state`. */
    private val json = Json { ignoreUnknownKeys = false }

    /** Криптографически стойкий генератор PKCE verifier. */
    private val secureRandom = SecureRandom()

    /** Выпускает новый подписанный Telegram login `state` и связанный с ним PKCE verifier. */
    fun issue(): IssuedTelegramLoginState {
        val expiresAt = nowProvider().plusSeconds(ttlSeconds)
        val payload = TelegramLoginStatePayload(
            redirectUri = redirectUri,
            codeVerifier = generatePkceVerifier(),
            expiresAtEpochSeconds = expiresAt.epochSecond,
        )
        val encodedPayload = payload.toSignedPayloadPart(json)
        val signature = sign(encodedPayload)
        return IssuedTelegramLoginState(
            state = "$encodedPayload.$signature",
            codeVerifier = payload.codeVerifier,
            redirectUri = payload.redirectUri,
        )
    }

    /** Проверяет подпись и срок действия ранее выпущенного Telegram login `state`. */
    fun verify(state: String): Result<VerifiedTelegramLoginState> {
        if (state.isBlank()) {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state is missing"))
        }
        val delimiterIndex = state.indexOf('.')
        if (delimiterIndex <= 0 || delimiterIndex == state.lastIndex) {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state has invalid format"))
        }
        val encodedPayload = state.substring(0, delimiterIndex)
        val signature = state.substring(delimiterIndex + 1)
        val expectedSignature = sign(encodedPayload)
        if (!constantTimeEquals(expectedSignature, signature)) {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state signature is invalid"))
        }
        val payload = runCatching {
            val decodedBytes = Base64.getUrlDecoder().decode(encodedPayload)
            json.decodeFromString(
                TelegramLoginStatePayload.serializer(),
                decodedBytes.toString(StandardCharsets.UTF_8),
            )
        }.getOrElse {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state payload is invalid"))
        }
        if (payload.redirectUri != redirectUri) {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state redirect URI mismatch"))
        }
        val expiresAt = Instant.ofEpochSecond(payload.expiresAtEpochSeconds)
        if (!expiresAt.isAfter(nowProvider())) {
            return Result.failure(InvalidTelegramAuthStateException("Telegram auth state is expired"))
        }
        return Result.success(
            VerifiedTelegramLoginState(
                codeVerifier = payload.codeVerifier,
                redirectUri = payload.redirectUri,
                expiresAt = expiresAt,
            ),
        )
    }

    /** Генерирует PKCE verifier из официального набора символов RFC 7636. */
    private fun generatePkceVerifier(length: Int = 64): String {
        return buildString(length) {
            repeat(length) {
                append(PKCE_ALPHABET[secureRandom.nextInt(PKCE_ALPHABET.length)])
            }
        }
    }

    /** Подписывает сериализованную часть `state` через HMAC-SHA256 и Base64URL. */
    private fun sign(payloadPart: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val digest = mac.doFinal(payloadPart.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Сравнивает подписи без утечки информации по времени выполнения. */
    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        val expectedBytes = expected.toByteArray(StandardCharsets.UTF_8)
        val actualBytes = actual.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private companion object {
        /** Разрешенный RFC 7636 alphabet для PKCE verifier. */
        const val PKCE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    }
}

/**
 * Подписываемая полезная нагрузка Telegram login `state`.
 *
 * @property redirectUri Redirect URI, зафиксированный при запуске auth flow.
 * @property codeVerifier PKCE verifier, связанный с текущей auth-попыткой.
 * @property expiresAtEpochSeconds Unix timestamp истечения допустимости `state`.
 */
@Serializable
private data class TelegramLoginStatePayload(
    val redirectUri: String,
    val codeVerifier: String,
    val expiresAtEpochSeconds: Long,
) {
    /** Сериализует payload в компактную Base64URL-часть для transport-safe `state`. */
    fun toSignedPayloadPart(json: Json): String {
        val rawJson = json.encodeToString(serializer(), this)
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8))
    }
}

/**
 * Ошибка недействительного Telegram auth `state`.
 *
 * @property message Безопасное машинно-читаемое описание причины.
 */
class InvalidTelegramAuthStateException(
    override val message: String,
) : IllegalArgumentException(message)
