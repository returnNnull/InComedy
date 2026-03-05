package com.bam.incomedy.server.auth.telegram

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TelegramAuthVerifier(
    private val botToken: String,
    private val maxAuthAgeSeconds: Long = 86400L,
) {
    fun verify(payload: TelegramVerifyRequest, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Result<VerifiedTelegramAuth> {
        validatePayload(payload, nowEpochSeconds)?.let { message ->
            return Result.failure(IllegalArgumentException(message))
        }

        val expectedHash = computeHash(payload)
        if (!constantTimeEquals(expectedHash, payload.hash.lowercase())) {
            return Result.failure(IllegalArgumentException("Invalid Telegram auth hash"))
        }

        return Result.success(
            VerifiedTelegramAuth(
                user = TelegramUser(
                    id = payload.id,
                    firstName = payload.firstName,
                    lastName = payload.lastName,
                    username = payload.username,
                    photoUrl = payload.photoUrl,
                ),
                authDate = payload.authDate,
            ),
        )
    }

    fun computeHash(payload: TelegramVerifyRequest): String {
        val dataCheckString = buildDataCheckString(payload)
        val secretKey = MessageDigest.getInstance("SHA-256")
            .digest(botToken.toByteArray(StandardCharsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        val digest = mac.doFinal(dataCheckString.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun buildDataCheckString(payload: TelegramVerifyRequest): String {
        val pairs = linkedMapOf<String, String>()
        pairs["auth_date"] = payload.authDate.toString()
        pairs["first_name"] = payload.firstName
        pairs["id"] = payload.id.toString()
        payload.lastName?.let { pairs["last_name"] = it }
        payload.photoUrl?.let { pairs["photo_url"] = it }
        payload.username?.let { pairs["username"] = it }

        return pairs
            .toSortedMap()
            .entries
            .joinToString("\n") { (key, value) -> "$key=$value" }
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        val left = expected.toByteArray(StandardCharsets.UTF_8)
        val right = actual.toByteArray(StandardCharsets.UTF_8)
        if (left.size != right.size) return false
        var result = 0
        for (i in left.indices) {
            result = result or (left[i].toInt() xor right[i].toInt())
        }
        return result == 0
    }

    private fun validatePayload(payload: TelegramVerifyRequest, nowEpochSeconds: Long): String? {
        if (payload.id <= 0L) return "Telegram id must be positive"
        if (payload.firstName.isBlank()) return "Telegram first_name is required"
        if (payload.firstName.length > 100) return "Telegram first_name is too long"
        if ((payload.lastName?.length ?: 0) > 100) return "Telegram last_name is too long"
        if (payload.authDate <= 0L) return "Telegram auth_date must be positive"
        if (payload.authDate > nowEpochSeconds + ALLOWED_CLOCK_SKEW_SECONDS) {
            return "Telegram auth payload has invalid auth_date"
        }
        if (nowEpochSeconds - payload.authDate > maxAuthAgeSeconds) {
            return "Telegram auth payload is expired"
        }
        if (!payload.hash.matches(HASH_REGEX)) return "Telegram hash is invalid"

        val username = payload.username
        if (!username.isNullOrBlank() && !username.matches(USERNAME_REGEX)) {
            return "Telegram username format is invalid"
        }

        val photoUrl = payload.photoUrl
        if (!photoUrl.isNullOrBlank()) {
            if (photoUrl.length > MAX_URL_LENGTH) return "Telegram photo_url is too long"
            val uri = runCatching { URI(photoUrl) }.getOrNull()
                ?: return "Telegram photo_url is invalid"
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return "Telegram photo_url must be https"
            }
        }
        return null
    }

    private companion object {
        val HASH_REGEX = Regex("^[a-f0-9]{64}$")
        val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{5,32}$")
        const val MAX_URL_LENGTH = 2048
        const val ALLOWED_CLOCK_SKEW_SECONDS = 300L
    }
}
