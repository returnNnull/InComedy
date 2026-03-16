package com.bam.incomedy.feature.auth.ui

import android.net.Uri
import com.bam.incomedy.domain.auth.AuthProviderType
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidExternalAuthIntentsTest {

    @Test
    fun `vk launch prefers vk app when package resolves`() {
        val plan = AndroidExternalAuthIntents.plan(
            effect = AuthEffect.OpenExternalAuth(
                provider = AuthProviderType.VK,
                url = "https://id.vk.ru/authorize?client_id=123",
            ),
            uri = Uri.parse("https://id.vk.ru/authorize?client_id=123"),
            canResolveIntent = { intent -> intent.`package` == "com.vkontakte.android" },
        )

        assertEquals("vk_app", plan.launchMode)
        assertEquals("com.vkontakte.android", plan.primaryIntent.`package`)
        assertEquals("https://id.vk.ru/authorize?client_id=123", plan.primaryIntent.dataString)
        val fallbackIntent = assertNotNull(plan.fallbackIntent)
        assertNull(fallbackIntent.`package`)
    }

    @Test
    fun `vk launch falls back to browser when vk app is unavailable`() {
        val plan = AndroidExternalAuthIntents.plan(
            effect = AuthEffect.OpenExternalAuth(
                provider = AuthProviderType.VK,
                url = "https://id.vk.ru/authorize?client_id=123",
            ),
            uri = Uri.parse("https://id.vk.ru/authorize?client_id=123"),
            canResolveIntent = { false },
        )

        assertEquals("browser_no_vk_app", plan.launchMode)
        assertNull(plan.primaryIntent.`package`)
        assertEquals("https://id.vk.ru/authorize?client_id=123", plan.primaryIntent.dataString)
        assertNull(plan.fallbackIntent)
    }

    @Test
    fun `non vk providers use browser launch only`() {
        val plan = AndroidExternalAuthIntents.plan(
            effect = AuthEffect.OpenExternalAuth(
                provider = AuthProviderType.TELEGRAM,
                url = "https://example.com/auth",
            ),
            uri = Uri.parse("https://example.com/auth"),
            canResolveIntent = { true },
        )

        assertEquals("browser", plan.launchMode)
        assertNull(plan.primaryIntent.`package`)
        assertNull(plan.fallbackIntent)
    }
}
