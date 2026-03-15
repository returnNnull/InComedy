package com.bam.incomedy.data.auth.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests parsing of VK callback payloads that arrive through mobile deep links or the HTTPS bridge.
 */
class VkCallbackPayloadParserTest {

    /** URL-encoded callback values should be decoded before the backend verify request is built. */
    @Test
    fun `decodes encoded vk callback payload values`() {
        val payload = parseVkCallbackPayload(
            "incomedy://auth/vk?code=vk%2Bcode%2Fvalue&state=signed_state&device_id=device%2F123",
        )

        assertNotNull(payload)
        assertEquals("vk+code/value", payload.code)
        assertEquals("signed_state", payload.state)
        assertEquals("device/123", payload.deviceId)
        assertEquals("browser_bridge", payload.clientSource)
    }

    /** Fragment-based payloads should be parsed the same way as query-based payloads. */
    @Test
    fun `parses vk callback payload from fragment`() {
        val payload = parseVkCallbackPayload(
            "incomedy://auth/vk#code=vk_code&state=signed_state&device_id=device123",
        )

        assertNotNull(payload)
        assertEquals("vk_code", payload.code)
        assertEquals("signed_state", payload.state)
        assertEquals("device123", payload.deviceId)
        assertEquals("browser_bridge", payload.clientSource)
    }

    /** Synthetic Android SDK callbacks should preserve the explicit source marker for backend diagnostics. */
    @Test
    fun `parses vk callback source marker when present`() {
        val payload = parseVkCallbackPayload(
            "incomedy://auth/vk/sdk?code=vk_code&state=signed_state&device_id=device123&client_source=android_sdk",
        )

        assertNotNull(payload)
        assertEquals("android_sdk", payload.clientSource)
    }
}
