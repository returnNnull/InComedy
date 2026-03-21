package com.bam.incomedy.server.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Unit-тесты runtime-конфига backend-а.
 *
 * Проверяют, что optional PSP-интеграция не мешает старту сервера без явного user-confirmed
 * enable-флага и что при включении сохраняется строгая валидация обязательных полей.
 */
class AppConfigTest {

    /** Проверяет, что YooKassa по умолчанию не активируется даже при наличии env-переменных. */
    @Test
    fun `yookassa stays disabled by default`() {
        val config = AppConfig.fromEnv(
            baseEnv() + mapOf(
                "YOOKASSA_SHOP_ID" to "shop-123",
                "YOOKASSA_SECRET_KEY" to "secret-123",
                "YOOKASSA_RETURN_URL" to "https://incomedy.test/payments/yookassa/return",
            ),
        )

        assertNull(config.yooKassa)
    }

    /** Проверяет, что явное включение YooKassa поднимает PSP-конфиг. */
    @Test
    fun `yookassa config is loaded only when explicitly enabled`() {
        val config = AppConfig.fromEnv(
            baseEnv() + mapOf(
                "YOOKASSA_ENABLED" to "true",
                "YOOKASSA_SHOP_ID" to "shop-123",
                "YOOKASSA_SECRET_KEY" to "secret-123",
                "YOOKASSA_RETURN_URL" to "https://incomedy.test/payments/yookassa/return",
                "YOOKASSA_API_BASE_URL" to "https://api.yookassa.test/v3",
                "YOOKASSA_CAPTURE" to "false",
            ),
        )

        assertEquals("shop-123", config.yooKassa?.shopId)
        assertEquals("secret-123", config.yooKassa?.secretKey)
        assertEquals("https://incomedy.test/payments/yookassa/return", config.yooKassa?.returnUrl)
        assertEquals("https://api.yookassa.test/v3", config.yooKassa?.apiBaseUrl)
        assertEquals(false, config.yooKassa?.capture)
    }

    /** Проверяет, что при явном включении отсутствующие обязательные поля по-прежнему валятся. */
    @Test
    fun `enabled yookassa still requires mandatory fields`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnv(
                baseEnv() + mapOf(
                    "YOOKASSA_ENABLED" to "true",
                ),
            )
        }

        assertEquals(
            "Environment variable 'YOOKASSA_SHOP_ID' is required when YooKassa checkout is enabled",
            error.message,
        )
    }

    /** Возвращает минимальный валидный backend env для конфигурационных unit-тестов. */
    private fun baseEnv(): Map<String, String> {
        return mapOf(
            "DB_URL" to "jdbc:postgresql://localhost:5432/incomedy",
            "DB_USER" to "postgres",
            "DB_PASSWORD" to "postgres",
            "JWT_SECRET" to "test-secret",
        )
    }
}
