package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.auth.IssuedAuthSession
import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.VkIdConfig
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.UserRepository
import java.time.Instant

class VkIdAuthService(
    private val config: VkIdConfig,
    private val loginStateCodec: VkIdLoginStateCodec,
    private val vkIdClient: VkIdClient,
    private val userRepository: UserRepository,
    private val tokenService: JwtSessionTokenService,
) {
    fun createLaunchRequest(): VkIdAuthLaunch {
        val issuedState = loginStateCodec.issue()
        return VkIdAuthLaunch(
            authUrl = vkIdClient.buildAuthorizeUrl(
                state = issuedState.state,
                codeChallenge = issuedState.codeChallenge,
            ),
            state = issuedState.state,
        )
    }

    fun verifyAndCreateSession(request: VkIdVerifyRequest): IssuedAuthSession {
        val verifiedState = loginStateCodec.verify(request.state).getOrThrow()
        val tokenResponse = vkIdClient.exchangeAuthorizationCode(
            code = request.code,
            state = request.state,
            deviceId = request.deviceId,
            verifiedState = verifiedState,
        )
        val userInfo = vkIdClient.loadUserInfo(tokenResponse.accessToken).user
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
        return IssuedAuthSession(
            provider = AuthProvider.VK.wireName,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
            user = storedUser,
        )
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
                    redirectUri = config.redirectUri,
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
)
