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
    /** Creates a browser launch URL plus optional Android SDK metadata for the current VK auth attempt. */
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
            providerClientId = config.androidClientId,
            providerCodeChallenge = issuedState.codeChallenge.takeIf { config.androidClientId != null },
            providerScopes = config.scopeTokens().takeIf { config.androidClientId != null } ?: emptyList(),
        )
    }

    /**
     * Verifies the signed VK auth state, exchanges the authorization code, and issues an internal session.
     *
     * The backend keeps browser/public-callback and Android SDK completions under the same signed state,
     * but selects the provider client/redirect pair from the sanitized completion source.
     */
    fun verifyAndCreateSession(request: VkIdVerifyRequest): VkIdIssuedSession {
        val verifiedState = loginStateCodec.verify(request.state).getOrThrow()
        val clientConfig = resolveClientConfig(request.clientSource)
        val tokenResponse = vkIdClient.exchangeAuthorizationCode(
            clientId = clientConfig.clientId,
            redirectUri = clientConfig.redirectUri,
            code = request.code,
            state = request.state,
            deviceId = request.deviceId,
            verifiedState = verifiedState,
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

    companion object {
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
    val providerClientId: String? = null,
    val providerCodeChallenge: String? = null,
    val providerScopes: List<String> = emptyList(),
)

data class VkIdVerifyRequest(
    val code: String,
    val state: String,
    val deviceId: String,
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

/** Splits VK scope config into a deterministic list for start-response SDK metadata. */
private fun VkIdConfig.scopeTokens(): List<String> {
    return scope.split(',', ' ')
        .map(String::trim)
        .filter(String::isNotBlank)
}
