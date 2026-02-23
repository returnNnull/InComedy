package com.bam.incomedy.data.auth.providers

internal fun buildUrl(
    base: String,
    params: Map<String, String>,
): String {
    val query = params.entries.joinToString("&") { (key, value) ->
        "${key.encodeUrlPart()}=${value.encodeUrlPart()}"
    }
    return "$base?$query"
}

private fun String.encodeUrlPart(): String {
    return buildString {
        this@encodeUrlPart.forEach { ch ->
            when {
                ch.isLetterOrDigit() || ch in listOf('-', '_', '.', '~') -> append(ch)
                else -> {
                    append('%')
                    append(ch.code.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}

