package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.auth.IssuedAuthSession
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.VkIdConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.UserRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

class VkIdAuthService(
    private val config: VkIdConfig,
    private val loginStateCodec: VkIdLoginStateCodec,
    private val vkIdClient: VkIdGateway,
    private val userRepository: UserRepository,
    private val tokenService: JwtSessionTokenService,
) {
    /** Возвращает `true`, если backend настроен для documented Android VK SDK code exchange. */
    fun isAndroidSdkConfigured(): Boolean {
        return !config.androidClientId.isNullOrBlank() && !config.androidRedirectUri.isNullOrBlank()
    }

    /** Создает browser/public-callback launch URL для текущей VK auth-попытки. */
    fun createLaunchRequest(): VkIdAuthLaunch {
        val issuedState = loginStateCodec.issue()
        return VkIdAuthLaunch(
            authUrl = vkIdClient.buildAuthorizeUrl(
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                state = issuedState.state,
                codeChallenge = issuedState.codeChallenge,
            ),
            state = issuedState.state,
        )
    }

    /**
     * Завершает VK authorization-code flow и выпускает внутреннюю InComedy-сессию.
     *
     * Browser/public callback продолжает жить на signed backend-issued `state`, а Android SDK path
     * следует официальной схеме VK и передает client-generated `state` + `codeVerifier` только на
     * этапе verify/code exchange.
     */
    fun verifyAndCreateSession(request: VkIdVerifyRequest): VkIdIssuedSession {
        val clientConfig = resolveClientConfig(request.clientSource)
        val exchangeState = validateStateValue(request.state)
        val codeVerifier = resolveCodeVerifier(
            clientSource = clientConfig.clientSource,
            request = request,
        )
        val tokenResponse = vkIdClient.exchangeAuthorizationCode(
            clientId = clientConfig.clientId,
            redirectUri = clientConfig.redirectUri,
            code = request.code,
            state = exchangeState,
            deviceId = request.deviceId,
            codeVerifier = codeVerifier,
        )
        val userInfo = vkIdClient.loadUserInfo(
            clientId = clientConfig.clientId,
            accessToken = tokenResponse.accessToken,
        ).user
        val displayName = listOfNotNull(userInfo.firstName, userInfo.lastName)
            .joinToString(" ")
            .trim()
            .ifBlank { "VK User" }
        val storedUser = userRepository.upsertVkIdentity(
            providerUserId = userInfo.userId,
            displayName = displayName,
            username = null,
            photoUrl = userInfo.avatar,
        )

        val now = Instant.now()
        val tokens = tokenService.issue(
            userId = storedUser.id,
            provider = AuthProvider.VK,
        )
        userRepository.storeRefreshToken(
            userId = storedUser.id,
            tokenHash = tokenService.refreshTokenHash(tokens.refreshToken),
            expiresAt = tokenService.refreshExpiryInstant(now),
        )
        return VkIdIssuedSession(
            session = IssuedAuthSession(
                provider = AuthProvider.VK.wireName,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresInSeconds = tokens.expiresInSeconds,
                user = storedUser,
            ),
            clientSource = clientConfig.clientSource,
        )
    }

    /** Возвращает PKCE verifier для выбранного VK completion path и валидирует его форму. */
    private fun resolveCodeVerifier(
        clientSource: VkIdClientSource,
        request: VkIdVerifyRequest,
    ): String {
        return when (clientSource) {
            VkIdClientSource.BROWSER_BRIDGE -> {
                val verifiedState = loginStateCodec.verify(request.state).getOrThrow()
                verifiedState.codeVerifier
            }
            VkIdClientSource.ANDROID_SDK -> validateCodeVerifier(request.codeVerifier)
        }
    }

    /** Resolves which VK client configuration should complete the current authorization code. */
    private fun resolveClientConfig(requestedSource: VkIdClientSource?): VkIdResolvedClientConfig {
        return when (requestedSource ?: VkIdClientSource.BROWSER_BRIDGE) {
            VkIdClientSource.BROWSER_BRIDGE -> VkIdResolvedClientConfig(
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                clientSource = VkIdClientSource.BROWSER_BRIDGE,
            )
            VkIdClientSource.ANDROID_SDK -> {
                val androidClientId = config.androidClientId
                    ?: throw InvalidVkIdAuthStateException("VK ID Android SDK auth is not configured")
                val androidRedirectUri = config.androidRedirectUri
                    ?: throw InvalidVkIdAuthStateException("VK ID Android SDK auth is not configured")
                VkIdResolvedClientConfig(
                    clientId = androidClientId,
                    redirectUri = androidRedirectUri,
                    clientSource = VkIdClientSource.ANDROID_SDK,
                )
            }
        }
    }

    /** Ограничивает клиентский state для Android SDK flow и отбрасывает заведомо некорректные значения. */
    private fun validateStateValue(state: String): String {
        if (state.isBlank()) {
            throw InvalidVkIdAuthStateException("VK ID auth state is missing")
        }
        if (state.length > MAX_CLIENT_STATE_LENGTH) {
            throw InvalidVkIdAuthStateException("VK ID auth state is too long")
        }
        return state
    }

    /** Валидирует Android SDK PKCE verifier по базовым ограничениям RFC 7636. */
    private fun validateCodeVerifier(codeVerifier: String?): String {
        val value = codeVerifier?.takeIf { it.isNotBlank() }
            ?: throw InvalidVkIdAuthStateException("VK ID Android SDK code verifier is missing")
        if (value.length !in MIN_CODE_VERIFIER_LENGTH..MAX_CODE_VERIFIER_LENGTH) {
            throw InvalidVkIdAuthStateException("VK ID Android SDK code verifier has invalid length")
        }
        if (!PKCE_CODE_VERIFIER_REGEX.matches(value)) {
            throw InvalidVkIdAuthStateException("VK ID Android SDK code verifier has invalid format")
        }
        return value
    }

    companion object {
        private const val MAX_CLIENT_STATE_LENGTH = 256
        private const val MIN_CODE_VERIFIER_LENGTH = 43
        private const val MAX_CODE_VERIFIER_LENGTH = 128
        private val PKCE_CODE_VERIFIER_REGEX = Regex("^[A-Za-z0-9._~-]{43,128}$")

        fun create(
            config: VkIdConfig,
            userRepository: UserRepository,
            tokenService: JwtSessionTokenService,
        ): VkIdAuthService {
            return VkIdAuthService(
                config = config,
                loginStateCodec = VkIdLoginStateCodec(
                    secret = config.stateSecret,
                    ttlSeconds = config.stateTtlSeconds,
                ),
                vkIdClient = VkIdClient(config),
                userRepository = userRepository,
                tokenService = tokenService,
            )
        }
    }
}

data class VkIdAuthLaunch(
    val authUrl: String,
    val state: String,
)

data class VkIdVerifyRequest(
    val code: String,
    val state: String,
    val deviceId: String,
    val codeVerifier: String? = null,
    val clientSource: VkIdClientSource? = null,
)

/** Resolved VK session issuance plus the safe completion source used for diagnostics. */
data class VkIdIssuedSession(
    val session: IssuedAuthSession,
    val clientSource: VkIdClientSource,
)

/** Sanitized VK completion source marker used by start/verify contracts and diagnostics. */
@Serializable
enum class VkIdClientSource {
    @SerialName("browser_bridge")
    BROWSER_BRIDGE,

    @SerialName("android_sdk")
    ANDROID_SDK,
}

/** Internal provider client/redirect pair used for one VK code exchange attempt. */
private data class VkIdResolvedClientConfig(
    val clientId: String,
    val redirectUri: String,
    val clientSource: VkIdClientSource,
)
