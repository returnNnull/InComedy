package com.bam.incomedy.server.payments.yookassa

import com.bam.incomedy.server.config.YooKassaConfig
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayException
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit-тесты server-side YooKassa checkout adapter-а.
 */
class YooKassaCheckoutGatewayTest {
    /** Проверяет, что gateway отправляет корректный payload и возвращает confirmation URL. */
    @Test
    fun `gateway builds yookassa request and returns checkout session`() {
        val transport = CapturingTransport(
            postResponse = YooKassaTransportResponse(
                statusCode = 200,
                body = """
                    {
                      "id":"payment-123",
                      "status":"pending",
                      "confirmation":{
                        "confirmation_url":"https://yookassa.test/confirmation/payment-123"
                      }
                    }
                """.trimIndent(),
            ),
        )
        val gateway = YooKassaCheckoutGateway(
            config = testConfig(),
            transport = transport,
        )

        val response = gateway.createCheckoutSession(
            TicketCheckoutGatewayRequest(
                orderId = "00000000-0000-0000-0000-000000000701",
                eventId = "00000000-0000-0000-0000-000000000702",
                currency = "RUB",
                totalMinor = 5100,
                description = "InComedy order 00000000",
                requestId = "223e4567-e89b-12d3-a456-426614174000",
            ),
        )

        assertEquals("yookassa", response.provider)
        assertEquals("payment-123", response.providerPaymentId)
        assertEquals("pending", response.providerStatus)
        assertEquals("https://yookassa.test/confirmation/payment-123", response.confirmationUrl)
        assertEquals("00000000-0000-0000-0000-000000000701", transport.idempotenceKey)
        assertTrue(transport.body.contains(""""value":"51.00""""))
        assertTrue(transport.body.contains(""""currency":"RUB""""))
        assertTrue(transport.body.contains(""""type":"redirect""""))
        assertTrue(transport.body.contains(""""order_id":"00000000-0000-0000-0000-000000000701""""))
        assertTrue(transport.body.contains(""""event_id":"00000000-0000-0000-0000-000000000702""""))
        assertTrue(transport.body.contains("order_id=00000000-0000-0000-0000-000000000701"))
        assertTrue(transport.authorizationHeader.startsWith("Basic "))
    }

    /** Проверяет, что отсутствие confirmation URL классифицируется как безопасная provider error. */
    @Test
    fun `gateway fails when yookassa response misses confirmation url`() {
        val gateway = YooKassaCheckoutGateway(
            config = testConfig(),
            transport = CapturingTransport(
                postResponse = YooKassaTransportResponse(
                    statusCode = 200,
                    body = """{"id":"payment-123","status":"pending","confirmation":{}}""",
                ),
            ),
        )

        val error = assertFailsWith<TicketCheckoutGatewayException> {
            gateway.createCheckoutSession(
                TicketCheckoutGatewayRequest(
                    orderId = "00000000-0000-0000-0000-000000000701",
                    eventId = "00000000-0000-0000-0000-000000000702",
                    currency = "RUB",
                    totalMinor = 5100,
                    description = "InComedy order 00000000",
                    requestId = "223e4567-e89b-12d3-a456-426614174000",
                ),
            )
        }

        assertEquals("checkout_provider_response_invalid", error.safeCode)
    }

    /** Проверяет, что gateway умеет загружать актуальный payment snapshot для webhook verification. */
    @Test
    fun `gateway loads current yookassa payment snapshot`() {
        val transport = CapturingTransport(
            postResponse = YooKassaTransportResponse(
                statusCode = 200,
                body = """
                    {
                      "id":"payment-123",
                      "status":"pending",
                      "confirmation":{
                        "confirmation_url":"https://yookassa.test/confirmation/payment-123"
                      }
                    }
                """.trimIndent(),
            ),
            getResponse = YooKassaTransportResponse(
                statusCode = 200,
                body = """
                    {
                      "id":"payment-123",
                      "status":"succeeded",
                      "amount":{"value":"51.00","currency":"RUB"},
                      "metadata":{
                        "order_id":"00000000-0000-0000-0000-000000000701",
                        "event_id":"00000000-0000-0000-0000-000000000702"
                      }
                    }
                """.trimIndent(),
            ),
        )
        val gateway = YooKassaCheckoutGateway(
            config = testConfig(),
            transport = transport,
        )

        val payment = gateway.getPayment("payment-123")

        assertEquals("yookassa", payment.provider)
        assertEquals("payment-123", payment.providerPaymentId)
        assertEquals("succeeded", payment.status)
        assertEquals("00000000-0000-0000-0000-000000000701", payment.orderId)
        assertEquals("00000000-0000-0000-0000-000000000702", payment.eventId)
        assertEquals(5100, payment.totalMinor)
        assertEquals("RUB", payment.currency)
        assertEquals("https://api.yookassa.test/v3/payments/payment-123", transport.lastGetUrl)
    }

    /** Возвращает стабильный тестовый config без live secrets. */
    private fun testConfig(): YooKassaConfig {
        return YooKassaConfig(
            shopId = "shop-123",
            secretKey = "secret-123",
            returnUrl = "https://incomedy.test/payments/yookassa/return",
            apiBaseUrl = "https://api.yookassa.test/v3",
            capture = true,
        )
    }

    /** Transport-заглушка, сохраняющая последний запрос для утверждений. */
    private class CapturingTransport(
        private val postResponse: YooKassaTransportResponse,
        private val getResponse: YooKassaTransportResponse = YooKassaTransportResponse(
            statusCode = 404,
            body = """{"type":"error"}""",
        ),
    ) : YooKassaTransport {
        lateinit var authorizationHeader: String
            private set

        lateinit var idempotenceKey: String
            private set

        lateinit var body: String
            private set

        lateinit var lastGetUrl: String
            private set

        override fun postJson(
            url: String,
            authorizationHeader: String,
            idempotenceKey: String,
            body: String,
        ): YooKassaTransportResponse {
            this.authorizationHeader = authorizationHeader
            this.idempotenceKey = idempotenceKey
            this.body = body
            assertEquals("https://api.yookassa.test/v3/payments", url)
            return postResponse
        }

        override fun getJson(
            url: String,
            authorizationHeader: String,
        ): YooKassaTransportResponse {
            this.authorizationHeader = authorizationHeader
            this.lastGetUrl = url
            return getResponse
        }
    }
}
