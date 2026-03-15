package com.bam.incomedy.server.auth.vk

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class VkIdLoginStateCodec(
    secret: String,
    private val ttlSeconds: Long,
    private val nowProvider: () -> Instant = Instant::now,
) {
    private val secretBytes = secret.toByteArray(StandardCharsets.UTF_8)
    private val json = Json { ignoreUnknownKeys = false }
    private val secureRandom = SecureRandom()

    fun issue(): IssuedVkIdLoginState {
        val expiresAt = nowProvider().plusSeconds(ttlSeconds)
        val codeVerifier = generatePkceVerifier()
        val payload = VkIdLoginStatePayload(
            codeVerifier = codeVerifier,
            expiresAtEpochSeconds = expiresAt.epochSecond,
        )
        val encodedPayload = payload.toSignedPayloadPart(json)
        val signature = sign(encodedPayload)
        return IssuedVkIdLoginState(
            state = "$encodedPayload.$signature",
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge(codeVerifier),
        )
    }

    fun verify(state: String): Result<VerifiedVkIdLoginState> {
        if (state.isBlank()) {
            return Result.failure(InvalidVkIdAuthStateException("VK ID auth state is missing"))
        }
        val delimiterIndex = state.indexOf('.')
        if (delimiterIndex <= 0 || delimiterIndex == state.lastIndex) {
            return Result.failure(InvalidVkIdAuthStateException("VK ID auth state has invalid format"))
        }
        val encodedPayload = state.substring(0, delimiterIndex)
        val signature = state.substring(delimiterIndex + 1)
        val expectedSignature = sign(encodedPayload)
        if (!MessageDigest.isEqual(expectedSignature.toByteArray(StandardCharsets.UTF_8), signature.toByteArray(StandardCharsets.UTF_8))) {
            return Result.failure(InvalidVkIdAuthStateException("VK ID auth state signature is invalid"))
        }
        val payload = runCatching {
            val decodedBytes = Base64.getUrlDecoder().decode(encodedPayload)
            json.decodeFromString(VkIdLoginStatePayload.serializer(), decodedBytes.toString(StandardCharsets.UTF_8))
        }.getOrElse {
            return Result.failure(InvalidVkIdAuthStateException("VK ID auth state payload is invalid"))
        }
        val expiresAt = Instant.ofEpochSecond(payload.expiresAtEpochSeconds)
        if (!expiresAt.isAfter(nowProvider())) {
            return Result.failure(InvalidVkIdAuthStateException("VK ID auth state is expired"))
        }
        return Result.success(
            VerifiedVkIdLoginState(
                codeVerifier = payload.codeVerifier,
                expiresAt = expiresAt,
            ),
        )
    }

    private fun sign(payloadPart: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val digest = mac.doFinal(payloadPart.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun generatePkceVerifier(length: Int = 64): String {
        return buildString(length) {
            repeat(length) {
                append(PKCE_ALPHABET[secureRandom.nextInt(PKCE_ALPHABET.length)])
            }
        }
    }

    private fun codeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private companion object {
        const val PKCE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
    }
}

data class IssuedVkIdLoginState(
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
)

data class VerifiedVkIdLoginState(
    val codeVerifier: String,
    val expiresAt: Instant,
)

@Serializable
private data class VkIdLoginStatePayload(
    val codeVerifier: String,
    val expiresAtEpochSeconds: Long,
) {
    fun toSignedPayloadPart(json: Json): String {
        val rawJson = json.encodeToString(serializer(), this)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawJson.toByteArray(StandardCharsets.UTF_8))
    }
}

class InvalidVkIdAuthStateException(
    override val message: String,
) : IllegalArgumentException(message)
