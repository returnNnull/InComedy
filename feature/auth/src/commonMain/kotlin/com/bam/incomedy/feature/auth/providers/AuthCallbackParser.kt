package com.bam.incomedy.feature.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthProviderType

data class ParsedAuthCallback(
    val provider: AuthProviderType,
    val code: String,
    val state: String,
)

object AuthCallbackParser {
    fun parse(url: String): ParsedAuthCallback? {
        val provider = providerFromUrl(url) ?: return null
        val params = queryParams(url)
        val state = params["state"].orEmpty()
        val code = when (provider) {
            AuthProviderType.TELEGRAM -> url
            AuthProviderType.VK, AuthProviderType.GOOGLE -> params["code"].orEmpty()
        }
        if (code.isBlank()) return null
        return ParsedAuthCallback(provider = provider, code = code, state = state)
    }

    private fun providerFromUrl(url: String): AuthProviderType? {
        val normalized = url.lowercase()
        return when {
            normalized.contains("/auth/telegram") -> AuthProviderType.TELEGRAM
            normalized.contains("/auth/vk") -> AuthProviderType.VK
            normalized.contains("/auth/google") -> AuthProviderType.GOOGLE
            else -> null
        }
    }

    private fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return emptyMap()
        return query.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = pair.substring(0, index)
                val value = pair.substring(index + 1)
                key to decodePercent(value)
            }
            .toMap()
    }

    private fun decodePercent(value: String): String {
        val sb = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '%' && index + 2 < value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    sb.append(decoded.toChar())
                    index += 3
                    continue
                }
            }
            if (ch == '+') sb.append(' ') else sb.append(ch)
            index++
        }
        return sb.toString()
    }
}

