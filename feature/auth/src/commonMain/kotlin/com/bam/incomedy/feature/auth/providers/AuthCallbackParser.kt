package com.bam.incomedy.feature.auth.providers

import com.bam.incomedy.feature.auth.domain.AuthProviderType

/**
 * Нормализованный результат разбора callback URL внешнего auth-провайдера.
 *
 * @property provider Провайдер, к которому относится callback.
 * @property code Код или сырое callback-значение для provider-specific exchange.
 * @property state Значение `state`, если оно присутствует в callback.
 */
data class ParsedAuthCallback(
    val provider: AuthProviderType,
    val code: String,
    val state: String,
)

/** Разбирает callback URL внешнего auth-провайдера в унифицированную модель. */
object AuthCallbackParser {
    /** Возвращает распознанный callback или `null`, если URL не относится к поддерживаемым провайдерам. */
    fun parse(url: String): ParsedAuthCallback? {
        val provider = providerFromUrl(url) ?: return null
        val params = callbackParams(url)
        val state = params["state"].orEmpty()
        val code = when (provider) {
            AuthProviderType.TELEGRAM -> url
            AuthProviderType.VK, AuthProviderType.GOOGLE -> params["code"].orEmpty()
        }
        if (code.isBlank()) return null
        return ParsedAuthCallback(provider = provider, code = code, state = state)
    }

    /** Определяет auth-провайдера по стабильному URL path pattern. */
    private fun providerFromUrl(url: String): AuthProviderType? {
        val normalized = url.lowercase()
        return when {
            normalized.contains("/auth/telegram") -> AuthProviderType.TELEGRAM
            normalized.contains("/auth/vk") -> AuthProviderType.VK
            normalized.contains("/auth/google") -> AuthProviderType.GOOGLE
            else -> null
        }
    }

    /** Собирает параметры из query и fragment части callback URL. */
    private fun callbackParams(url: String): Map<String, String> {
        return buildMap {
            putAll(parseQueryLike(url.substringAfter('?', "").substringBefore('#')))
            putAll(parseQueryLike(url.substringAfter('#', "")))
        }
    }

    /** Разбирает query-like часть callback URL в карту параметров. */
    private fun parseQueryLike(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = pair.substring(0, index)
                val encodedValue = pair.substring(index + 1)
                key to decodePercent(encodedValue)
            }
            .toMap()
    }

    /** Выполняет percent-decoding query/fragment значений без платформенных helper-ов. */
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
