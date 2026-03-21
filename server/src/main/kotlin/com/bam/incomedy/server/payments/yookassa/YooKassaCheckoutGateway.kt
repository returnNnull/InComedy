package com.bam.incomedy.server.payments.yookassa

import com.bam.incomedy.server.config.YooKassaConfig
import com.bam.incomedy.server.ticketing.TicketCheckoutGateway
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayException
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayPaymentSnapshot
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayRequest
import com.bam.incomedy.server.ticketing.TicketCheckoutGatewayResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

/**
 * Реализация внешнего checkout handoff через YooKassa.
 *
 * Адаптер создает redirect-payment только для уже зафиксированного `TicketOrder`, не меняя
 * внутренних инвариантов order/inventory и оставляя payment confirmation отдельным следующим slice-ом.
 */
class YooKassaCheckoutGateway(
    private val config: YooKassaConfig,
    private val transport: YooKassaTransport = JavaNetYooKassaTransport(),
    private val json: Json = Json { ignoreUnknownKeys = false },
) : TicketCheckoutGateway {
    override val provider: String = "yookassa"

    /** Создает внешний redirect checkout в YooKassa и возвращает confirmation URL. */
    override fun createCheckoutSession(request: TicketCheckoutGatewayRequest): TicketCheckoutGatewayResponse {
        val returnUrl = buildReturnUrl(
            baseUrl = config.returnUrl,
            orderId = request.orderId,
        )
        val payload = YooKassaCreatePaymentRequest(
            amount = YooKassaAmount(
                value = formatMinorAmount(request.totalMinor),
                currency = request.currency,
            ),
            capture = config.capture,
            confirmation = YooKassaConfirmationRequest(
                type = "redirect",
                returnUrl = returnUrl,
            ),
            description = request.description.take(MAX_DESCRIPTION_LENGTH),
            metadata = mapOf(
                "order_id" to request.orderId,
                "event_id" to request.eventId,
            ),
        )
        val response = transport.postJson(
            url = "${config.apiBaseUrl.trimEnd('/')}/payments",
            authorizationHeader = basicAuthorizationHeader(
                username = config.shopId,
                password = config.secretKey,
            ),
            idempotenceKey = request.orderId,
            body = json.encodeToString(payload),
        )
        if (response.statusCode !in 200..299) {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_unavailable",
                safeMessage = "YooKassa create payment request failed",
            )
        }
        val parsed = runCatching {
            json.decodeFromString(YooKassaCreatePaymentResponse.serializer(), response.body)
        }.getOrElse {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa create payment response is invalid",
            )
        }
        val confirmationUrl = parsed.confirmation?.confirmationUrl
            ?.takeIf(String::isNotBlank)
            ?: throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa response does not contain confirmation URL",
            )
        return TicketCheckoutGatewayResponse(
            provider = provider,
            providerPaymentId = parsed.id,
            providerStatus = parsed.status,
            confirmationUrl = confirmationUrl,
            returnUrl = returnUrl,
        )
    }

    /** Загружает актуальный payment snapshot из YooKassa для webhook/status verification. */
    override fun getPayment(
        providerPaymentId: String,
    ): TicketCheckoutGatewayPaymentSnapshot {
        val response = transport.getJson(
            url = "${config.apiBaseUrl.trimEnd('/')}/payments/$providerPaymentId",
            authorizationHeader = basicAuthorizationHeader(
                username = config.shopId,
                password = config.secretKey,
            ),
        )
        if (response.statusCode !in 200..299) {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_unavailable",
                safeMessage = "YooKassa payment lookup failed",
            )
        }
        val parsed = runCatching {
            json.decodeFromString(YooKassaPaymentDetailsResponse.serializer(), response.body)
        }.getOrElse {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa payment lookup response is invalid",
            )
        }
        val orderId = parsed.metadata["order_id"]
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa payment metadata does not contain order_id",
            )
        val eventId = parsed.metadata["event_id"]
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa payment metadata does not contain event_id",
            )
        return TicketCheckoutGatewayPaymentSnapshot(
            provider = provider,
            providerPaymentId = parsed.id,
            status = parsed.status,
            orderId = orderId,
            eventId = eventId,
            totalMinor = parseMinorAmount(parsed.amount.value),
            currency = parsed.amount.currency,
        )
    }

    /** Формирует полный merchant-controlled return URL с order correlation query parameter-ом. */
    private fun buildReturnUrl(
        baseUrl: String,
        orderId: String,
    ): String {
        val delimiter = if ('?' in baseUrl) "&" else "?"
        val encodedOrderId = URLEncoder.encode(orderId, StandardCharsets.UTF_8)
        return "$baseUrl${delimiter}order_id=$encodedOrderId"
    }

    /** Преобразует сумму из minor units в строковый decimal-формат YooKassa. */
    private fun formatMinorAmount(totalMinor: Int): String {
        return BigDecimal(totalMinor)
            .movePointLeft(2)
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString()
    }

    /** Преобразует строковую decimal-сумму YooKassa обратно в minor units. */
    private fun parseMinorAmount(value: String): Int {
        val decimal = runCatching {
            BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY)
        }.getOrElse {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa amount format is invalid",
            )
        }
        return runCatching {
            decimal.movePointRight(2).intValueExact()
        }.getOrElse {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_response_invalid",
                safeMessage = "YooKassa amount cannot be converted to minor units",
            )
        }
    }

    /** Строит Basic auth header для server-side YooKassa API calls. */
    private fun basicAuthorizationHeader(
        username: String,
        password: String,
    ): String {
        val raw = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }

    private companion object {
        /** Ограничивает описание платежа в пределах разумной длины для PSP request-а. */
        const val MAX_DESCRIPTION_LENGTH = 128
    }
}

/**
 * Узкий transport seam для unit-тестирования YooKassa gateway без live network.
 */
interface YooKassaTransport {
    /** Выполняет POST JSON-запрос к YooKassa API и возвращает только нужные gateway данные. */
    fun postJson(
        url: String,
        authorizationHeader: String,
        idempotenceKey: String,
        body: String,
    ): YooKassaTransportResponse

    /** Выполняет GET-запрос к YooKassa API и возвращает только нужные gateway данные. */
    fun getJson(
        url: String,
        authorizationHeader: String,
    ): YooKassaTransportResponse
}

/**
 * Короткая transport-обертка над HTTP-ответом YooKassa.
 *
 * @property statusCode HTTP-статус ответа.
 * @property body Тело ответа как сырой JSON.
 */
data class YooKassaTransportResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Transport по умолчанию для живых server-side запросов к YooKassa.
 */
class JavaNetYooKassaTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : YooKassaTransport {
    /** Отправляет синхронный JSON POST к YooKassa API через JDK HttpClient. */
    override fun postJson(
        url: String,
        authorizationHeader: String,
        idempotenceKey: String,
        body: String,
    ): YooKassaTransportResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", authorizationHeader)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Idempotence-Key", idempotenceKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = execute(request)
        return YooKassaTransportResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    /** Отправляет синхронный GET к YooKassa API для проверки текущего статуса платежа. */
    override fun getJson(
        url: String,
        authorizationHeader: String,
    ): YooKassaTransportResponse {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", authorizationHeader)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = execute(request)
        return YooKassaTransportResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }

    /** Унифицирует transport-level обработку сетевых ошибок YooKassa API. */
    private fun execute(
        request: HttpRequest,
    ): HttpResponse<String> {
        return runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrElse {
            throw TicketCheckoutGatewayException(
                safeCode = "checkout_provider_unavailable",
                safeMessage = "YooKassa API request failed",
            )
        }
    }
}

/** DTO тела запроса создания платежа в YooKassa. */
@Serializable
private data class YooKassaCreatePaymentRequest(
    val amount: YooKassaAmount,
    val capture: Boolean,
    val confirmation: YooKassaConfirmationRequest,
    val description: String,
    val metadata: Map<String, String>,
)

/** DTO суммы платежа YooKassa. */
@Serializable
private data class YooKassaAmount(
    val value: String,
    val currency: String,
)

/** DTO redirect confirmation блока YooKassa. */
@Serializable
private data class YooKassaConfirmationRequest(
    val type: String,
    @SerialName("return_url")
    val returnUrl: String,
)

/** Минимальный ответ YooKassa, нужный для checkout handoff. */
@Serializable
private data class YooKassaCreatePaymentResponse(
    val id: String,
    val status: String,
    val confirmation: YooKassaConfirmationResponse? = null,
)

/** Блок confirmation из ответа YooKassa. */
@Serializable
private data class YooKassaConfirmationResponse(
    @SerialName("confirmation_url")
    val confirmationUrl: String? = null,
)

/** Минимальный snapshot платежа YooKassa для webhook/status verification. */
@Serializable
private data class YooKassaPaymentDetailsResponse(
    val id: String,
    val status: String,
    val amount: YooKassaAmount,
    val metadata: Map<String, String> = emptyMap(),
)
