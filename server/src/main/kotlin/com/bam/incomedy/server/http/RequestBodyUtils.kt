package com.bam.incomedy.server.http

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.json.Json

class PayloadTooLargeException(
    maxBytes: Int,
) : IllegalArgumentException("Request body exceeds $maxBytes bytes")

suspend inline fun <reified T> ApplicationCall.receiveJsonBodyLimited(
    json: Json,
    maxBytes: Int,
): Result<T> {
    val contentLength = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (contentLength != null && contentLength > maxBytes) {
        return Result.failure(PayloadTooLargeException(maxBytes))
    }

    val channel = receiveChannel()
    val packet = channel.readRemaining(maxBytes.toLong() + 1)
    val bytes = packet.readBytes()
    if (bytes.size > maxBytes || !channel.isClosedForRead) {
        return Result.failure(PayloadTooLargeException(maxBytes))
    }

    return runCatching {
        json.decodeFromString<T>(bytes.decodeToString())
    }
}
