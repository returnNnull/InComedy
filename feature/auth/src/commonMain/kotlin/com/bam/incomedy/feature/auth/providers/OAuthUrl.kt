package com.bam.incomedy.feature.auth.providers

internal fun buildUrl(base: String, params: Map<String, String>): String {
    val query = params.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }
    return "$base?$query"
}

private fun encode(raw: String): String {
    val unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
    val bytes = raw.encodeToByteArray()
    val builder = StringBuilder()
    for (byte in bytes) {
        val ch = byte.toInt().toChar()
        if (unreserved.indexOf(ch) >= 0) {
            builder.append(ch)
        } else {
            val code = byte.toInt() and 0xFF
            builder.append('%')
            builder.append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
