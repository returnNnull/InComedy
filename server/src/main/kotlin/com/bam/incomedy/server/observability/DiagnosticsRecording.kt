package com.bam.incomedy.server.observability

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path

/**
 * Записывает диагностическое событие по текущему Ktor call.
 *
 * @param call Исходный HTTP call.
 * @param stage Безопасная стадия обработки.
 * @param status HTTP-статус или статус результата.
 * @param safeErrorCode Безопасный машинный код ошибки.
 * @param metadata Дополнительные безопасные low-cardinality метаданные.
 */
fun DiagnosticsStore.recordCall(
    call: ApplicationCall,
    stage: String,
    status: Int,
    safeErrorCode: String? = null,
    metadata: Map<String, String> = emptyMap(),
) {
    record(
        DiagnosticsEventInput(
            requestId = call.callId ?: "n/a",
            method = call.request.httpMethod.value,
            route = call.request.path(),
            stage = stage,
            status = status,
            safeErrorCode = safeErrorCode,
            metadata = metadata,
        ),
    )
}
