package com.bam.incomedy.server.auth.session

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bam.incomedy.server.config.JwtConfig
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.random.Random

class JwtSessionTokenService(
    private val config: JwtConfig,
) : SessionTokenService {
    private val algorithm = Algorithm.HMAC256(config.secret)

    override fun issue(userId: String, telegramUserId: Long): SessionTokens {
        val now = Instant.now()
        val accessExpiry = now.plusSeconds(config.accessTtlSeconds)

        val accessToken = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("provider", "telegram")
            .withClaim("telegram_user_id", telegramUserId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(accessExpiry))
            .withJWTId(UUID.randomUUID().toString())
            .sign(algorithm)

        val refreshToken = randomToken()
        return SessionTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = config.accessTtlSeconds,
        )
    }

    fun refreshTokenHash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun refreshExpiryInstant(now: Instant = Instant.now()): Instant {
        return now.plusSeconds(config.refreshTtlSeconds)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        Random.Default.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

