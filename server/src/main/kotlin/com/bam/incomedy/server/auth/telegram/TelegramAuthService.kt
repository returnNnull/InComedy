package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionUser
import com.bam.incomedy.server.db.TelegramUserRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class TelegramAuthService(
    private val verifier: TelegramAuthVerifier,
    private val repository: TelegramUserRepository,
    private val tokenService: JwtSessionTokenService,
) {
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)

    fun verifyAndCreateSession(request: TelegramVerifyRequest): Result<TelegramAuthResult> {
        val verified = verifier.verify(request).getOrElse { return Result.failure(it) }
        val storedUser = repository.upsert(verified.user)

        val tokens = tokenService.issue(
            userId = storedUser.id,
            telegramUserId = storedUser.telegramId,
        )
        repository.storeRefreshToken(
            userId = storedUser.id,
            tokenHash = tokenService.refreshTokenHash(tokens.refreshToken),
            expiresAt = tokenService.refreshExpiryInstant(Instant.now()),
        )
        logger.info(
            "auth.telegram.session.issued userId={} telegramId={} accessTtlSeconds={}",
            storedUser.id,
            storedUser.telegramId,
            tokens.expiresInSeconds,
        )

        val displayName = listOfNotNull(storedUser.firstName, storedUser.lastName).joinToString(" ").trim()
        return Result.success(
            TelegramAuthResult(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresInSeconds = tokens.expiresInSeconds,
                user = SessionUser(
                    id = storedUser.id,
                    displayName = displayName.ifBlank { storedUser.username ?: "Telegram user" },
                    username = storedUser.username,
                    photoUrl = storedUser.photoUrl,
                ),
            ),
        )
    }
}

data class TelegramAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: SessionUser,
)
