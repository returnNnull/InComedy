package com.bam.incomedy.server.auth.telegram

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.auth.session.SessionUser
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.UserRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/** Ошибка повторного использования уже принятого Telegram auth-утверждения. */
class ReplayedTelegramAuthException : IllegalArgumentException("Telegram auth payload was already used")

/**
 * Координирует Telegram login flow: старт OIDC-авторизации, code exchange и выпуск внутренней сессии.
 *
 * @property loginStateCodec Сервис выпуска и проверки серверно подписанного `state`.
 * @property oidcClient Клиент официального Telegram OIDC flow.
 * @property repository Репозиторий внутренних пользователей и refresh token-ов.
 * @property tokenService Сервис выпуска внутренних JWT-сессий.
 */
class TelegramAuthService(
    private val loginStateCodec: TelegramLoginStateCodec,
    private val oidcClient: TelegramOidcGateway,
    private val repository: UserRepository,
    private val tokenService: JwtSessionTokenService,
) {
    /** Структурированный logger Telegram auth orchestration слоя. */
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)

    /** Выпускает стартовую Telegram auth-конфигурацию с official OIDC URL и signed state. */
    fun createLaunchRequest(): Result<TelegramAuthLaunch> {
        return runCatching {
            val issuedState = loginStateCodec.issue()
            TelegramAuthLaunch(
                authUrl = oidcClient.buildAuthorizationUrl(
                    state = issuedState.state,
                    codeVerifier = issuedState.codeVerifier,
                ),
                state = issuedState.state,
            )
        }
    }

    /** Завершает Telegram auth callback и выпускает внутреннюю backend-сессию. */
    fun verifyAndCreateSession(request: TelegramVerifyRequest): Result<TelegramAuthResult> {
        val verifiedState = loginStateCodec.verify(request.state).getOrElse { return Result.failure(it) }
        val verified = oidcClient.exchangeAndVerify(
            code = request.code,
            state = verifiedState,
        ).getOrElse { return Result.failure(it) }
        val accepted = repository.registerTelegramAuthAssertion(
            assertionHash = verified.assertionHash,
            telegramUserId = verified.user.id,
            expiresAt = verified.replayExpiresAt,
        )
        if (!accepted) {
            logger.warn(
                "auth.telegram.verify.replay_detected telegramId={} replayExpiresAt={}",
                verified.user.id,
                verified.replayExpiresAt,
            )
            return Result.failure(ReplayedTelegramAuthException())
        }
        val storedUser = repository.upsertTelegramIdentity(verified.user)

        val tokens = tokenService.issue(
            userId = storedUser.id,
            provider = AuthProvider.TELEGRAM,
        )
        repository.storeRefreshToken(
            userId = storedUser.id,
            tokenHash = tokenService.refreshTokenHash(tokens.refreshToken),
            expiresAt = tokenService.refreshExpiryInstant(Instant.now()),
        )
        logger.info(
            "auth.telegram.session.issued userId={} accessTtlSeconds={}",
            storedUser.id,
            tokens.expiresInSeconds,
        )

        return Result.success(
            TelegramAuthResult(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresInSeconds = tokens.expiresInSeconds,
                user = SessionUser(
                    id = storedUser.id,
                    displayName = storedUser.displayName,
                    username = storedUser.username,
                    photoUrl = storedUser.photoUrl,
                ),
            ),
        )
    }
}

/**
 * Успешный результат Telegram auth после выпуска внутренней backend-сессии.
 *
 * @property accessToken Внутренний access token.
 * @property refreshToken Внутренний refresh token.
 * @property expiresInSeconds TTL access token в секундах.
 * @property user Нормализованный backend-пользователь текущей сессии.
 */
data class TelegramAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: SessionUser,
)
