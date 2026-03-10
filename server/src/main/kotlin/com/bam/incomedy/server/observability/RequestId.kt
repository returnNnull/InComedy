package com.bam.incomedy.server.observability

import java.util.UUID

internal fun isValidRequestId(value: String): Boolean {
    return runCatching { UUID.fromString(value) }.isSuccess
}
