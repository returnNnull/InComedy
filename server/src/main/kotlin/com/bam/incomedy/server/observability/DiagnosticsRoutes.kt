package com.bam.incomedy.server.observability

import com.bam.incomedy.server.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * Operator-only API для безопасного чтения последних серверных диагностических событий.
 */
object DiagnosticsRoutes {
    /** Регистрирует endpoint чтения diagnostics events. */
    fun register(route: Route, diagnosticsStore: DiagnosticsStore, accessToken: String) {
        route.get("/api/v1/diagnostics/events") {
            val providedToken = call.request.headers[DIAGNOSTICS_ACCESS_TOKEN_HEADER].orEmpty()
            if (!constantTimeEquals(providedToken, accessToken)) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Invalid diagnostics token"),
                )
                return@get
            }

            val status = call.request.queryParameters["status"]?.toIntOrNull()
            if (call.request.queryParameters["status"] != null && status == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid status filter"))
                return@get
            }

            val from = parseInstant(call.request.queryParameters["from"])
            if (call.request.queryParameters["from"] != null && from == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid from filter"))
                return@get
            }

            val to = parseInstant(call.request.queryParameters["to"])
            if (call.request.queryParameters["to"] != null && to == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", "Invalid to filter"))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
            val events = diagnosticsStore.query(
                DiagnosticsQuery(
                    requestId = call.request.queryParameters["request_id"]?.trim()?.takeIf(String::isNotBlank),
                    routePrefix = call.request.queryParameters["route_prefix"]?.trim()?.takeIf(String::isNotBlank),
                    stage = call.request.queryParameters["stage"]?.trim()?.takeIf(String::isNotBlank),
                    status = status,
                    from = from,
                    to = to,
                    limit = limit,
                ),
            )
            call.respond(
                HttpStatusCode.OK,
                DiagnosticsEventsResponse(
                    events = events.map(StoredDiagnosticsEvent::toResponse),
                ),
            )
        }
    }

    /** Пытается распарсить ISO-8601 instant из query string. */
    private fun parseInstant(value: String?): Instant? {
        val rawValue = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching { Instant.parse(rawValue) }.getOrNull()
    }

    /** Выполняет constant-time сравнение токенов доступа. */
    private fun constantTimeEquals(left: String, right: String): Boolean {
        val leftBytes = left.toByteArray(StandardCharsets.UTF_8)
        val rightBytes = right.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(leftBytes, rightBytes)
    }

    /** Заголовок с operator-only токеном чтения диагностики. */
    private const val DIAGNOSTICS_ACCESS_TOKEN_HEADER = "X-Diagnostics-Token"

    /** Значение limit по умолчанию для чтения последних событий. */
    private const val DEFAULT_LIMIT = 50

    /** Верхняя граница limit, чтобы защитить endpoint от слишком тяжелых ответов. */
    private const val MAX_LIMIT = 200
}

/**
 * DTO списка диагностических событий для retrieval endpoint.
 *
 * @property events Newest-first список санитизированных событий.
 */
@kotlinx.serialization.Serializable
data class DiagnosticsEventsResponse(
    val events: List<DiagnosticsEventResponse>,
)

/**
 * DTO одного диагностического события, возвращаемого оператору.
 *
 * @property id Монотонный идентификатор события.
 * @property recordedAt ISO-8601 время записи на сервере.
 * @property requestId Request correlation identifier.
 * @property method HTTP-метод исходного запроса.
 * @property route HTTP-путь исходного запроса.
 * @property stage Короткая стадия обработки.
 * @property status HTTP-статус или статус результата стадии.
 * @property safeErrorCode Безопасный машинный код ошибки.
 * @property metadata Санитизированные low-cardinality атрибуты.
 */
@kotlinx.serialization.Serializable
data class DiagnosticsEventResponse(
    val id: Long,
    val recordedAt: String,
    val requestId: String,
    val method: String,
    val route: String,
    val stage: String,
    val status: Int,
    val safeErrorCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/** Преобразует внутреннее stored event в публичный diagnostics response. */
private fun StoredDiagnosticsEvent.toResponse(): DiagnosticsEventResponse {
    return DiagnosticsEventResponse(
        id = id,
        recordedAt = recordedAt.toString(),
        requestId = requestId,
        method = method,
        route = route,
        stage = stage,
        status = status,
        safeErrorCode = safeErrorCode,
        metadata = metadata,
    )
}
