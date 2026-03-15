package com.bam.incomedy.server.auth.vk

import com.bam.incomedy.server.auth.session.JwtSessionTokenService
import com.bam.incomedy.server.config.JwtConfig
import com.bam.incomedy.server.config.VkIdConfig
import com.bam.incomedy.server.support.InMemoryTelegramUserRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Unit tests for VK auth service source selection between browser callback and Android SDK flows.
 */
class VkIdAuthServiceTest {

    /** Browser launch should expose optional Android SDK metadata when a dedicated Android VK client is configured. */
    @Test
    fun `create launch request returns android sdk metadata when configured`() {
        val gateway = FakeVkIdGateway()
        val service = testAuthService(gateway = gateway)

        val launch = service.createLaunchRequest()

        assertEquals("vk-android-client-id", launch.providerClientId)
        assertNotNull(launch.providerCodeChallenge)
        assertEquals(listOf("vkid.personal_info"), launch.providerScopes)
        assertEquals("vk-browser-client-id", gateway.lastAuthorizeClientId)
        assertEquals("https://incomedy.ru/auth/vk/callback", gateway.lastAuthorizeRedirectUri)
    }

    /** Browser callback verification must exchange the code with the browser/public-callback VK client pair. */
    @Test
    fun `verify uses browser vk client by default`() {
        val gateway = FakeVkIdGateway()
        val service = testAuthService(gateway = gateway)
        val state = service.createLaunchRequest().state

        val auth = service.verifyAndCreateSession(
            VkIdVerifyRequest(
                code = "browser_code",
                state = state,
                deviceId = "device-1",
            ),
        )

        assertEquals(VkIdClientSource.BROWSER_BRIDGE, auth.clientSource)
        assertEquals("vk-browser-client-id", gateway.lastExchangeClientId)
        assertEquals("https://incomedy.ru/auth/vk/callback", gateway.lastExchangeRedirectUri)
        assertEquals("vk-browser-client-id", gateway.lastUserInfoClientId)
    }

    /** Android SDK verification must switch code exchange and user-info calls to the Android VK client pair. */
    @Test
    fun `verify uses android vk client when android sdk source is declared`() {
        val gateway = FakeVkIdGateway()
        val service = testAuthService(gateway = gateway)
        val state = service.createLaunchRequest().state

        val auth = service.verifyAndCreateSession(
            VkIdVerifyRequest(
                code = "android_code",
                state = state,
                deviceId = "device-2",
                clientSource = VkIdClientSource.ANDROID_SDK,
            ),
        )

        assertEquals(VkIdClientSource.ANDROID_SDK, auth.clientSource)
        assertEquals("vk-android-client-id", gateway.lastExchangeClientId)
        assertEquals("vk123456://vk.ru/blank.html", gateway.lastExchangeRedirectUri)
        assertEquals("vk-android-client-id", gateway.lastUserInfoClientId)
    }

    /** Android SDK verify requests should fail cleanly when the backend has no Android VK client configured. */
    @Test
    fun `verify rejects android sdk source when android vk client is not configured`() {
        val gateway = FakeVkIdGateway()
        val service = testAuthService(
            gateway = gateway,
            config = VkIdConfig(
                clientId = "vk-browser-client-id",
                redirectUri = "https://incomedy.ru/auth/vk/callback",
                scope = "vkid.personal_info",
                stateSecret = "vk-state-secret",
                stateTtlSeconds = 600L,
            ),
        )
        val state = service.createLaunchRequest().state

        val failure = assertFailsWith<InvalidVkIdAuthStateException> {
            service.verifyAndCreateSession(
                VkIdVerifyRequest(
                    code = "android_code",
                    state = state,
                    deviceId = "device-2",
                    clientSource = VkIdClientSource.ANDROID_SDK,
                ),
            )
        }

        assertEquals("VK ID Android SDK auth is not configured", failure.message)
    }

    /** Builds a VK auth service backed by a fake gateway and in-memory user/session infrastructure. */
    private fun testAuthService(
        gateway: VkIdGateway,
        config: VkIdConfig = VkIdConfig(
            clientId = "vk-browser-client-id",
            redirectUri = "https://incomedy.ru/auth/vk/callback",
            androidClientId = "vk-android-client-id",
            androidRedirectUri = "vk123456://vk.ru/blank.html",
            scope = "vkid.personal_info",
            stateSecret = "vk-state-secret",
            stateTtlSeconds = 600L,
        ),
    ): VkIdAuthService {
        return VkIdAuthService(
            config = config,
            loginStateCodec = VkIdLoginStateCodec(
                secret = config.stateSecret,
                ttlSeconds = config.stateTtlSeconds,
                nowProvider = { Instant.parse("2026-03-15T00:00:00Z") },
            ),
            vkIdClient = gateway,
            userRepository = InMemoryTelegramUserRepository(),
            tokenService = JwtSessionTokenService(
                JwtConfig(
                    issuer = "test",
                    secret = "0123456789abcdef0123456789abcdef",
                    accessTtlSeconds = 3600L,
                    refreshTtlSeconds = 86400L,
                ),
            ),
        )
    }
}

/** Fake VK gateway that records which VK client id and redirect pair the service selected. */
private class FakeVkIdGateway : VkIdGateway {
    var lastAuthorizeClientId: String? = null
    var lastAuthorizeRedirectUri: String? = null
    var lastExchangeClientId: String? = null
    var lastExchangeRedirectUri: String? = null
    var lastUserInfoClientId: String? = null

    override fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String {
        lastAuthorizeClientId = clientId
        lastAuthorizeRedirectUri = redirectUri
        return "https://id.vk.ru/authorize?client_id=$clientId&redirect_uri=$redirectUri&state=$state&code_challenge=$codeChallenge"
    }

    override fun exchangeAuthorizationCode(
        clientId: String,
        redirectUri: String,
        code: String,
        state: String,
        deviceId: String,
        verifiedState: VerifiedVkIdLoginState,
    ): VkIdTokenResponse {
        lastExchangeClientId = clientId
        lastExchangeRedirectUri = redirectUri
        return VkIdTokenResponse(
            accessToken = "vk_access_token",
            refreshToken = "vk_refresh_token",
            expiresIn = 3600L,
            state = state,
        )
    }

    override fun loadUserInfo(clientId: String, accessToken: String): VkIdUserInfoResponse {
        lastUserInfoClientId = clientId
        return VkIdUserInfoResponse(
            user = VkIdUserInfo(
                userId = "vk_user_1",
                firstName = "VK",
                lastName = "User",
                avatar = null,
            ),
        )
    }
}
