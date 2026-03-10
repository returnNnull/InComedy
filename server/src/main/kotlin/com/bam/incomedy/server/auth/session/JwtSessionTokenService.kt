package com.bam.incomedy.server.auth.session

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.db.AuthProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class JwtSessionTokenService(
    private val config: JwtConfig,
) : SessionTokenService {
    private val secureRandom = SecureRandom()
    private val algorithm = Algorithm.HMAC256(config.secret)
    private val verifier = JWT
        .require(algorithm)
        .withIssuer(config.issuer)
        .build()

    override fun issue(userId: String, provider: AuthProvider): SessionTokens {
        val now = Instant.now()
        val accessExpiry = now.plusSeconds(config.accessTtlSeconds)

        val accessToken = JWT.create()
            .withIssuer(config.issuer)
            .withSubject(userId)
            .withClaim("provider", provider.wireName)
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

    fun verifyAccessToken(accessToken: String): Result<VerifiedAccessToken> {
        return runCatching {
            val decoded = verifier.verify(accessToken)
            val userId = decoded.subject ?: error("Missing access token subject")
            val provider = decoded.getClaim("provider").asString() ?: "unknown"
            val issuedAt = decoded.issuedAt?.toInstant() ?: error("Missing access token issued-at")
            VerifiedAccessToken(
                userId = userId,
                provider = provider,
                issuedAt = issuedAt,
            )
        }.recoverCatching { cause ->
            if (cause is JWTVerificationException) {
                throw IllegalArgumentException("Invalid access token")
            }
            throw cause
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

data class VerifiedAccessToken(
    val userId: String,
    val provider: String,
    val issuedAt: Instant,
)
