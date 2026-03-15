package com.bam.incomedy.feature.auth.ui

import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidVkIdSdkAuthTest {

    @Test
    fun `vk sdk config reports unavailable when client id is missing`() {
        val config = AndroidVkIdSdkConfig(
            clientId = "",
            redirectHost = "vk.ru",
            redirectScheme = "vk123456",
            requestedScopes = setOf("vkid.personal_info"),
            isEnabled = true,
        )

        assertFalse(config.canUseOneTap())
        assertEquals("missing_client_id", config.unavailableReason())
    }

    @Test
    fun `vk auth attempt generates pkce compatible values`() {
        val attempt = AndroidVkIdSdkAuth.newAuthAttempt()

        assertTrue(attempt.state.length >= 32)
        assertTrue(attempt.codeVerifier.length in 43..128)
        assertEquals(43, attempt.codeChallenge.length)
    }

    @Test
    fun `vk sdk callback url includes android source marker and code verifier`() {
        val callbackUrl = buildVkSdkCallbackUrl(
            code = "vk-code",
            state = "client-state",
            deviceId = "device-123",
            codeVerifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0123456789-._~",
        )
        val uri = Uri.parse(callbackUrl)

        assertEquals("incomedy", uri.scheme)
        assertEquals("auth", uri.host)
        assertEquals("/vk/sdk", uri.path)
        assertEquals("vk-code", uri.getQueryParameter("code"))
        assertEquals("client-state", uri.getQueryParameter("state"))
        assertEquals("device-123", uri.getQueryParameter("device_id"))
        assertEquals(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0123456789-._~",
            uri.getQueryParameter("code_verifier"),
        )
        assertEquals("android_sdk", uri.getQueryParameter("client_source"))
    }
}
