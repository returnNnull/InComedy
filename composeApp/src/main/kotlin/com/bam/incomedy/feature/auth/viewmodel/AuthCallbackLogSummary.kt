package com.bam.incomedy.feature.auth.viewmodel

/**
 * Строит безопасную summary-строку для auth callback URL без логирования сырого payload.
 *
 * Возвращает только форму callback-а: схема, host/path, присутствие query/fragment и ключевых полей.
 */
internal fun safeAuthCallbackSummary(callbackUrl: String?): String {
    if (callbackUrl.isNullOrBlank()) {
        return "hasUrl=false"
    }
    val normalized = callbackUrl.trim()
    val params = extractCallbackParams(normalized)
    val providerHint = providerHintFromUrl(normalized)
    return buildString {
        append("hasUrl=true")
        append(" providerHint=").append(providerHint)
        append(" scheme=").append(extractScheme(normalized))
        append(" host=").append(extractHost(normalized))
        append(" path=").append(extractPath(normalized))
        append(" hasQuery=").append('?' in normalized)
        append(" hasFragment=").append('#' in normalized)
        append(" statePresent=").append(params["state"].isNullOrBlank().not())
        append(" hasCode=").append(params["code"].isNullOrBlank().not())
        append(" hasTgAuthResult=").append(params["tgAuthResult"].isNullOrBlank().not())
        append(" hasTelegramId=").append(params["id"].isNullOrBlank().not())
        append(" hasAuthDate=").append(params["auth_date"].isNullOrBlank().not())
        append(" hasHash=").append(params["hash"].isNullOrBlank().not())
    }
}

/** Определяет провайдер auth callback по безопасному URL pattern. */
private fun providerHintFromUrl(url: String): String {
    val normalized = url.lowercase()
    return when {
        normalized.contains("/auth/telegram") -> "telegram"
        normalized.contains("/auth/vk") -> "vk"
        normalized.contains("/auth/google") -> "google"
        else -> "unknown"
    }
}

/** Извлекает схему URI без чтения чувствительных query values. */
private fun extractScheme(url: String): String {
    return url.substringBefore("://", missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
        ?.take(16)
        ?: "n/a"
}

/** Извлекает host URI без query/fragment payload. */
private fun extractHost(url: String): String {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    return afterScheme.substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .takeIf(String::isNotBlank)
        ?.take(48)
        ?: "n/a"
}

/** Извлекает path URI без query/fragment payload. */
private fun extractPath(url: String): String {
    val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
    val rawPath = afterScheme.substringAfter('/', missingDelimiterValue = "")
        .substringBefore('?')
        .substringBefore('#')
        .take(64)
    return if (rawPath.isBlank()) "/" else "/$rawPath"
}

/** Извлекает только имена/наличие query и fragment параметров без логирования их значений. */
private fun extractCallbackParams(url: String): Map<String, String> {
    return buildMap {
        putAll(parseParams(url.substringAfter('?', "").substringBefore('#')))
        putAll(parseParams(url.substringAfter('#', "")))
    }
}

/** Разбирает query-like часть callback-а в карту параметров. */
private fun parseParams(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    return value
        .split('&')
        .mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) return@mapNotNull null
            pair.substring(0, index) to pair.substring(index + 1)
        }
        .toMap()
}
