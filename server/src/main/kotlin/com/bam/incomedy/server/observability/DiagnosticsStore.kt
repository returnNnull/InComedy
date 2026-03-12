package com.bam.incomedy.server.observability

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Входная модель санитизированного диагностического события.
 *
 * @property requestId Request correlation identifier, возвращаемый клиенту через `X-Request-ID`.
 * @property method HTTP-метод исходного запроса.
 * @property route HTTP-путь без query string.
 * @property stage Короткая бизнес-/техническая стадия обработки запроса.
 * @property status HTTP-статус или статус результата стадии.
 * @property safeErrorCode Безопасный машинный код ошибки без сырых деталей.
 * @property metadata Дополнительные безопасные атрибуты низкой кардинальности.
 */
data class DiagnosticsEventInput(
    val requestId: String,
    val method: String,
    val route: String,
    val stage: String,
    val status: Int,
    val safeErrorCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Сохраненное диагностическое событие с серверным временем записи.
 *
 * @property id Монотонный идентификатор события внутри текущего процесса.
 * @property recordedAt Момент записи события на сервере.
 * @property requestId Request correlation identifier.
 * @property method HTTP-метод запроса.
 * @property route HTTP-путь запроса.
 * @property stage Короткая стадия обработки.
 * @property status HTTP-статус или статус результата стадии.
 * @property safeErrorCode Безопасный машинный код ошибки.
 * @property metadata Санитизированные метаданные стадии.
 */
data class StoredDiagnosticsEvent(
    val id: Long,
    val recordedAt: Instant,
    val requestId: String,
    val method: String,
    val route: String,
    val stage: String,
    val status: Int,
    val safeErrorCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Фильтр чтения диагностических событий.
 *
 * @property requestId Точное совпадение request correlation id.
 * @property routePrefix Фильтр по префиксу HTTP-пути.
 * @property stage Точное совпадение стадии.
 * @property status Точное совпадение HTTP-статуса.
 * @property from Нижняя временная граница включительно.
 * @property to Верхняя временная граница включительно.
 * @property limit Максимальное число событий в ответе.
 */
data class DiagnosticsQuery(
    val requestId: String? = null,
    val routePrefix: String? = null,
    val stage: String? = null,
    val status: Int? = null,
    val from: Instant? = null,
    val to: Instant? = null,
    val limit: Int = 50,
)

/**
 * Абстракция хранилища безопасной серверной диагностики.
 */
interface DiagnosticsStore {
    /** Сохраняет очередное диагностическое событие. */
    fun record(event: DiagnosticsEventInput)

    /** Возвращает отфильтрованный список последних событий, newest-first. */
    fun query(filter: DiagnosticsQuery): List<StoredDiagnosticsEvent>
}

/**
 * In-memory bounded ring buffer для последних санитизированных диагностических событий.
 *
 * @property retentionLimit Максимальное число удерживаемых событий.
 * @property now Поставщик текущего времени для прод-кода и тестов.
 */
class InMemoryDiagnosticsStore(
    private val retentionLimit: Int,
    private val now: () -> Instant = { Instant.now() },
) : DiagnosticsStore {
    /** Очередь последних событий в порядке записи. */
    private val events = ArrayDeque<StoredDiagnosticsEvent>()

    /** Монотонный счетчик локальных идентификаторов событий. */
    private val nextId = AtomicLong(1L)

    init {
        require(retentionLimit > 0) { "retentionLimit must be positive" }
    }

    /** Сохраняет новое событие и вытесняет самый старый элемент при переполнении буфера. */
    @Synchronized
    override fun record(event: DiagnosticsEventInput) {
        val sanitized = sanitize(event)
        events.addLast(
            StoredDiagnosticsEvent(
                id = nextId.getAndIncrement(),
                recordedAt = now(),
                requestId = sanitized.requestId,
                method = sanitized.method,
                route = sanitized.route,
                stage = sanitized.stage,
                status = sanitized.status,
                safeErrorCode = sanitized.safeErrorCode,
                metadata = sanitized.metadata,
            ),
        )
        while (events.size > retentionLimit) {
            events.removeFirst()
        }
    }

    /** Возвращает newest-first выборку по заданным фильтрам. */
    @Synchronized
    override fun query(filter: DiagnosticsQuery): List<StoredDiagnosticsEvent> {
        val safeLimit = filter.limit.coerceIn(1, MAX_QUERY_LIMIT)
        return events
            .asReversed()
            .asSequence()
            .filter { event -> filter.requestId == null || event.requestId == filter.requestId }
            .filter { event -> filter.routePrefix == null || event.route.startsWith(filter.routePrefix) }
            .filter { event -> filter.stage == null || event.stage == filter.stage }
            .filter { event -> filter.status == null || event.status == filter.status }
            .filter { event -> filter.from == null || !event.recordedAt.isBefore(filter.from) }
            .filter { event -> filter.to == null || !event.recordedAt.isAfter(filter.to) }
            .take(safeLimit)
            .toList()
    }

    /** Санитизирует входное событие перед записью в буфер. */
    private fun sanitize(event: DiagnosticsEventInput): DiagnosticsEventInput {
        return DiagnosticsEventInput(
            requestId = sanitizeScalar(event.requestId, REQUEST_ID_MAX_LENGTH, fallback = "n/a"),
            method = sanitizeScalar(event.method, METHOD_MAX_LENGTH, fallback = "UNKNOWN"),
            route = sanitizeScalar(event.route, ROUTE_MAX_LENGTH, fallback = "/unknown"),
            stage = sanitizeScalar(event.stage, STAGE_MAX_LENGTH, fallback = "unknown"),
            status = event.status.coerceIn(0, 999),
            safeErrorCode = event.safeErrorCode
                ?.takeIf(String::isNotBlank)
                ?.let { sanitizeScalar(it, ERROR_CODE_MAX_LENGTH, fallback = "unknown_error") },
            metadata = sanitizeMetadata(event.metadata),
        )
    }

    /** Укорачивает и очищает одно строковое поле от перевода строк и лишней длины. */
    private fun sanitizeScalar(value: String, maxLength: Int, fallback: String): String {
        val normalized = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(maxLength)
        return normalized.ifBlank { fallback }
    }

    /** Ограничивает объем и кардинальность диагностических метаданных. */
    private fun sanitizeMetadata(metadata: Map<String, String>): Map<String, String> {
        return metadata.entries
            .asSequence()
            .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
            .take(MAX_METADATA_ENTRIES)
            .associate { (key, value) ->
                sanitizeScalar(key, METADATA_KEY_MAX_LENGTH, fallback = "meta") to
                    sanitizeScalar(value, METADATA_VALUE_MAX_LENGTH, fallback = "n/a")
            }
    }

    private companion object {
        /** Максимальный размер выборки одного diagnostics query. */
        const val MAX_QUERY_LIMIT = 200

        /** Максимальная длина request id в сохраненной диагностике. */
        const val REQUEST_ID_MAX_LENGTH = 64

        /** Максимальная длина HTTP-метода в сохраненной диагностике. */
        const val METHOD_MAX_LENGTH = 16

        /** Максимальная длина HTTP-пути в сохраненной диагностике. */
        const val ROUTE_MAX_LENGTH = 160

        /** Максимальная длина стадии обработки в сохраненной диагностике. */
        const val STAGE_MAX_LENGTH = 80

        /** Максимальная длина безопасного кода ошибки. */
        const val ERROR_CODE_MAX_LENGTH = 80

        /** Максимальное число метаданных, разрешенных на одно событие. */
        const val MAX_METADATA_ENTRIES = 10

        /** Максимальная длина ключа безопасной метадаты. */
        const val METADATA_KEY_MAX_LENGTH = 40

        /** Максимальная длина значения безопасной метадаты. */
        const val METADATA_VALUE_MAX_LENGTH = 160
    }
}
