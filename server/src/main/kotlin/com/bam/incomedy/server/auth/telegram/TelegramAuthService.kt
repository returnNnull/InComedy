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
    private val launchUri: String,
) {
    /** Структурированный logger Telegram auth orchestration слоя. */
    private val logger = LoggerFactory.getLogger(TelegramAuthService::class.java)

    /** Выпускает стартовую Telegram auth-конфигурацию с first-party launch URL и signed state. */
    fun createLaunchRequest(): Result<TelegramAuthLaunch> {
        return runCatching {
            val issuedState = loginStateCodec.issue()
            TelegramAuthLaunch(
                authUrl = buildLaunchUrl(issuedState.state),
                state = issuedState.state,
            )
        }
    }

    /** Восстанавливает официальный Telegram OIDC URL для first-party launch bridge page. */
    fun resolveOfficialAuthUrl(state: String): Result<String> {
        val verifiedState = loginStateCodec.verify(state).getOrElse { return Result.failure(it) }
        return runCatching {
            oidcClient.buildAuthorizationUrl(
                state = state,
                codeVerifier = verifiedState.codeVerifier,
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
                provider = AuthProvider.TELEGRAM.wireName,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresInSeconds = tokens.expiresInSeconds,
                user = SessionUser(
                    id = storedUser.id,
                    displayName = storedUser.displayName,
                    username = storedUser.username,
                    photoUrl = storedUser.photoUrl,
                ),
                roles = storedUser.roles.map { it.wireName }.sorted(),
                activeRole = storedUser.activeRole?.wireName,
                linkedProviders = storedUser.linkedProviders.map { it.wireName }.sorted(),
            ),
        )
    }

    /** Собирает first-party launch URL на домене InComedy, который уже инициирует Telegram browser auth. */
    private fun buildLaunchUrl(state: String): String {
        val delimiter = if ('?' in launchUri) '&' else '?'
        return buildString {
            append(launchUri)
            append(delimiter)
            append("state=")
            append(percentEncode(state))
        }
    }

    /** Выполняет percent-encoding state без platform-specific URI builder-ов. */
    private fun percentEncode(value: String): String {
        val safe = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        val builder = StringBuilder(value.length * 2)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val intValue = byte.toInt() and 0xFF
            val charValue = intValue.toChar()
            if (charValue in safe) {
                builder.append(charValue)
            } else {
                builder.append('%')
                builder.append(intValue.toString(16).uppercase().padStart(2, '0'))
            }
        }
        return builder.toString()
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
    val provider: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: SessionUser,
    val roles: List<String>,
    val activeRole: String?,
    val linkedProviders: List<String>,
)
