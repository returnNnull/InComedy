package com.bam.incomedy.feature.auth.ui

import android.net.Uri
import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
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
    fun `vk sdk config can handle matching vk launch effect`() {
        val config = AndroidVkIdSdkConfig(
            clientId = "vk-android-client-id",
            redirectHost = "vk.ru",
            redirectScheme = "vk123456",
            isEnabled = true,
        )

        assertTrue(config.canHandle(matchingVkEffect()))
    }

    @Test
    fun `vk sdk config falls back when server client id does not match local client id`() {
        val config = AndroidVkIdSdkConfig(
            clientId = "vk-android-client-id",
            redirectHost = "vk.ru",
            redirectScheme = "vk123456",
            isEnabled = true,
        )
        val effect = matchingVkEffect(
            providerClientId = "vk-other-client-id",
        )

        assertFalse(config.canHandle(effect))
        assertEquals("client_id_mismatch", config.fallbackReason(effect))
    }

    @Test
    fun `vk sdk callback url includes android source marker`() {
        val callbackUrl = buildVkSdkCallbackUrl(
            code = "vk-code",
            state = "signed-state",
            deviceId = "device-123",
        )
        val uri = Uri.parse(callbackUrl)

        assertEquals("incomedy", uri.scheme)
        assertEquals("auth", uri.host)
        assertEquals("/vk/sdk", uri.path)
        assertEquals("vk-code", uri.getQueryParameter("code"))
        assertEquals("signed-state", uri.getQueryParameter("state"))
        assertEquals("device-123", uri.getQueryParameter("device_id"))
        assertEquals("android_sdk", uri.getQueryParameter("client_source"))
    }

    /** Builds a representative VK launch effect carrying server-issued SDK metadata. */
    private fun matchingVkEffect(
        providerClientId: String = "vk-android-client-id",
    ): AuthEffect.OpenExternalAuth {
        return AuthEffect.OpenExternalAuth(
            provider = AuthProviderType.VK,
            url = "https://id.vk.ru/authorize?client_id=vk-browser-client-id",
            state = "signed-state",
            providerClientId = providerClientId,
            providerCodeChallenge = "pkce-challenge",
            providerScopes = setOf("vkid.personal_info"),
        )
    }
}
