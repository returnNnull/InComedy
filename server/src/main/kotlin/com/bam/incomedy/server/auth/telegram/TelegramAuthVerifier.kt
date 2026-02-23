package com.bam.incomedy.server.auth.telegram

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

class TelegramAuthVerifier(
    private val botToken: String,
    private val maxAuthAgeSeconds: Long = 86400L,
) {
    fun verify(payload: TelegramVerifyRequest, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Result<VerifiedTelegramAuth> {
        if (payload.firstName.isBlank()) {
            return Result.failure(IllegalArgumentException("Telegram first_name is required"))
        }
        if (payload.hash.isBlank()) {
            return Result.failure(IllegalArgumentException("Telegram hash is required"))
        }

        val age = abs(nowEpochSeconds - payload.authDate)
        if (age > maxAuthAgeSeconds) {
            return Result.failure(IllegalArgumentException("Telegram auth payload is expired"))
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
}

