package com.bam.incomedy.server.auth.vk

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression-тесты разбора VK JSON-ответов с числовым `user_id`.
 */
class VkIdClientParsingTest {

    private val parser = Json { ignoreUnknownKeys = true }

    /** Token exchange response с numeric `user_id` не должен ломать backend verify flow. */
    @Test
    fun `token response accepts numeric user id`() {
        val response = parser.decodeFromString(
            VkIdTokenResponse.serializer(),
            """
            {
              "access_token": "access-token",
              "refresh_token": "refresh-token",
              "expires_in": 3600,
              "user_id": 54484048,
              "state": "vk_state"
            }
            """.trimIndent(),
        )

        assertEquals("54484048", response.userId)
    }

    /** User info response с numeric `user_id` тоже должен приводиться к строке. */
    @Test
    fun `user info response accepts numeric user id`() {
        val response = parser.decodeFromString(
            VkIdUserInfoResponse.serializer(),
            """
            {
              "user": {
                "user_id": 54484048,
                "first_name": "Ivan",
                "last_name": "Petrov"
              }
            }
            """.trimIndent(),
        )

        assertEquals("54484048", response.user.userId)
    }
}
