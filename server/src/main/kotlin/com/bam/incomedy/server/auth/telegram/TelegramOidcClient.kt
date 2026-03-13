package com.bam.incomedy.server.auth.telegram

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Клиент официального Telegram OIDC login flow (`/auth`, `/token`, JWKS).
 */
interface TelegramOidcGateway {
    /** Строит официальный Telegram authorization URL для browser-based login flow. */
    fun buildAuthorizationUrl(state: String, codeVerifier: String): String

    /** Обменивает authorization code на проверенный Telegram профиль. */
    fun exchangeAndVerify(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<VerifiedTelegramAuth>
}

/**
 * Реальная реализация клиента официального Telegram OIDC login flow (`/auth`, `/token`, JWKS).
 *
 * @property clientId Telegram login client id, выданный через BotFather.
 * @property clientSecret Telegram login client secret.
 * @property redirectUri Зарегистрированный Telegram redirect URI.
 * @property transport HTTP transport для token/JWKS запросов.
 * @property parser JSON-парсер Telegram OIDC ответов.
 * @property nowProvider Источник текущего времени для валидации `id_token`.
 */
class TelegramOidcClient(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String,
    private val transport: TelegramOidcTransport = JdkTelegramOidcTransport(),
    private val parser: Json = Json { ignoreUnknownKeys = true },
    private val nowProvider: () -> Instant = Instant::now,
) : TelegramOidcGateway {
    /** Последний кэш JWKS для уменьшения количества сетевых запросов. */
    @Volatile
    private var jwksCache: CachedTelegramJwks? = null

    /** Строит официальный Telegram authorization URL для browser-based login flow. */
    override fun buildAuthorizationUrl(state: String, codeVerifier: String): String {
        if (state.isBlank()) {
            throw IllegalArgumentException("Telegram auth state is missing")
        }
        if (codeVerifier.isBlank()) {
            throw IllegalArgumentException("Telegram PKCE verifier is missing")
        }
        val codeChallenge = base64UrlSha256(codeVerifier)
        return buildUrl(
            base = AUTHORIZATION_ENDPOINT,
            params = linkedMapOf(
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "response_type" to "code",
                "scope" to "openid profile phone",
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            ),
        )
    }

    /** Обменивает Telegram authorization code на `id_token` и валидирует его подпись/claims. */
    override fun exchangeAndVerify(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<VerifiedTelegramAuth> {
        if (code.isBlank()) {
            return Result.failure(TelegramOidcExchangeException("Telegram authorization code is missing"))
        }
        val tokenResponse = exchangeCodeForTokens(code = code, state = state).getOrElse { return Result.failure(it) }
        val verifiedToken = verifyIdToken(tokenResponse.idToken).getOrElse { return Result.failure(it) }
        return Result.success(
            VerifiedTelegramAuth(
                user = verifiedToken.user,
                assertionHash = sha256(tokenResponse.idToken),
                replayExpiresAt = verifiedToken.expiresAt,
            ),
        )
    }

    /** Выполняет Telegram `/token` exchange для authorization code. */
    private fun exchangeCodeForTokens(
        code: String,
        state: VerifiedTelegramLoginState,
    ): Result<TelegramTokenExchangeResponse> {
        val httpResponse = transport.postForm(
            url = TOKEN_ENDPOINT,
            formFields = linkedMapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to state.redirectUri,
                "client_id" to clientId,
                "code_verifier" to state.codeVerifier,
            ),
            basicAuthUsername = clientId,
            basicAuthPassword = clientSecret,
        ).getOrElse {
            return Result.failure(TelegramOidcExchangeException("Telegram token exchange failed"))
        }
        if (httpResponse.statusCode !in 200..299) {
            return Result.failure(TelegramOidcExchangeException("Telegram token exchange failed"))
        }
        return runCatching {
            parser.decodeFromString(TelegramTokenExchangeResponse.serializer(), httpResponse.body)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(TelegramOidcExchangeException("Telegram token exchange response is invalid")) },
        )
    }

    /** Проверяет подпись и обязательные claims Telegram `id_token`. */
    private fun verifyIdToken(idToken: String): Result<VerifiedTelegramIdToken> {
        return runCatching {
            val decodedToken = JWT.decode(idToken)
            if (!decodedToken.algorithm.equals("RS256", ignoreCase = true)) {
                error("Telegram id_token signing algorithm is invalid")
            }
            val keyId = decodedToken.keyId?.takeIf { it.isNotBlank() }
                ?: error("Telegram id_token key id is missing")
            val publicKey = lookupPublicKey(keyId)
            val jwt = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(ISSUER)
                .withAudience(clientId)
                .acceptLeeway(ID_TOKEN_CLOCK_SKEW_SECONDS)
                .build()
                .verify(idToken)
            val telegramId = jwt.subject?.toLongOrNull()
                ?: error("Telegram id_token subject is invalid")
            val name = jwt.getClaim("name").asString()?.trim().orEmpty()
            val username = jwt.getClaim("preferred_username").asString()?.trim().takeIf { !it.isNullOrBlank() }
            val picture = jwt.getClaim("picture").asString()?.trim().takeIf { !it.isNullOrBlank() }
            val displayName = name.ifBlank { username ?: "Telegram user" }
            val expiresAt = jwt.expiresAt?.toInstant()
                ?: error("Telegram id_token exp is missing")
            VerifiedTelegramIdToken(
                user = TelegramUser(
                    id = telegramId,
                    firstName = displayName,
                    lastName = null,
                    username = username,
                    photoUrl = picture,
                ),
                expiresAt = expiresAt,
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(TelegramIdTokenVerificationException("Telegram id_token verification failed")) },
        )
    }

    /** Возвращает RSA public key для `kid` из официального Telegram JWKS. */
    private fun lookupPublicKey(keyId: String): RSAPublicKey {
        val cachedKeys = loadJwks()
        val jwk = cachedKeys.keys.firstOrNull { it.kid == keyId }
            ?: error("Telegram JWKS key is missing")
        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(jwk.n))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(jwk.e))
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
    }

    /** Загружает JWKS из кэша или официального Telegram endpoint. */
    private fun loadJwks(): TelegramJwksResponse {
        val cached = jwksCache
        val now = nowProvider()
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.value
        }
        val rawJwks = transport.get(JWKS_URI).getOrElse {
            throw TelegramIdTokenVerificationException("Telegram JWKS request failed")
        }
        val parsedJwks = runCatching {
            parser.decodeFromString(TelegramJwksResponse.serializer(), rawJwks)
        }.getOrElse {
            throw TelegramIdTokenVerificationException("Telegram JWKS response is invalid")
        }
        jwksCache = CachedTelegramJwks(
            value = parsedJwks,
            expiresAt = now.plus(JWKS_CACHE_TTL),
        )
        return parsedJwks
    }

    /** Вычисляет Base64URL SHA-256 от PKCE verifier. */
    private fun base64UrlSha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Строит URL с percent-encoded query-параметрами. */
    private fun buildUrl(base: String, params: Map<String, String>): String {
        return buildString {
            append(base)
            append('?')
            append(
                params.entries.joinToString("&") { (key, value) ->
                    "${percentEncode(key)}=${percentEncode(value)}"
                },
            )
        }
    }

    /** Выполняет percent-encoding для query components без сторонних зависимостей. */
    private fun percentEncode(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val safe = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
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

    /** Вычисляет SHA-256 хэш строкового значения для replay-защиты. */
    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        /** OIDC issuer, опубликованный Telegram. */
        const val ISSUER = "https://oauth.telegram.org"

        /** Официальный Telegram authorization endpoint. */
        const val AUTHORIZATION_ENDPOINT = "$ISSUER/auth"

        /** Официальный Telegram token endpoint. */
        const val TOKEN_ENDPOINT = "$ISSUER/token"

        /** Официальный Telegram JWKS endpoint. */
        const val JWKS_URI = "$ISSUER/.well-known/jwks.json"

        /** Допустимый clock skew при валидации Telegram `id_token`. */
        const val ID_TOKEN_CLOCK_SKEW_SECONDS = 30L

        /** Срок кэширования Telegram JWKS в памяти процесса. */
        val JWKS_CACHE_TTL: Duration = Duration.ofMinutes(15)
    }
}

/**
 * Успешно проверенный Telegram `id_token`.
 *
 * @property user Нормализованный Telegram профиль.
 * @property expiresAt Время истечения действия проверенного `id_token`.
 */
private data class VerifiedTelegramIdToken(
    val user: TelegramUser,
    val expiresAt: Instant,
)

/**
 * Кэш JWKS-ключей Telegram.
 *
 * @property value Текущее значение JWKS.
 * @property expiresAt Момент истечения локального кэша.
 */
private data class CachedTelegramJwks(
    val value: TelegramJwksResponse,
    val expiresAt: Instant,
)

/**
 * DTO ответа Telegram `/token`.
 *
 * @property idToken Проверяемый OIDC `id_token`.
 */
@Serializable
private data class TelegramTokenExchangeResponse(
    @SerialName("id_token")
    val idToken: String,
)

/**
 * DTO ответа Telegram JWKS endpoint.
 *
 * @property keys Список доступных публичных ключей Telegram OIDC.
 */
@Serializable
private data class TelegramJwksResponse(
    val keys: List<TelegramJwk>,
)

/**
 * DTO одного RSA-ключа из Telegram JWKS.
 *
 * @property kid Идентификатор ключа.
 * @property n Base64URL modulus RSA-ключа.
 * @property e Base64URL exponent RSA-ключа.
 */
@Serializable
private data class TelegramJwk(
    val kid: String,
    val n: String,
    val e: String,
)

/**
 * Ошибка обмена Telegram authorization code на токены.
 *
 * @property message Безопасное описание ошибки code exchange.
 */
class TelegramOidcExchangeException(
    override val message: String,
) : IllegalArgumentException(message)

/**
 * Ошибка валидации Telegram `id_token`.
 *
 * @property message Безопасное описание ошибки OIDC token verification.
 */
class TelegramIdTokenVerificationException(
    override val message: String,
) : IllegalArgumentException(message)
