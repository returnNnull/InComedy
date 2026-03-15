package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

/**
 * Shared sanitized client auth logger.
 *
 * The logger keeps messages bounded and low-sensitivity so platform adapters can use native logging
 * backends without leaking raw callback URLs, tokens, or multiline exception text.
 */
object AuthFlowLogger {
    /** Records one auth-flow event through the current platform logging backend. */
    fun event(
        stage: String,
        provider: AuthProviderType? = null,
        details: String? = null,
    ) {
        val safeStage = sanitize(value = stage, maxLength = 80, fallback = "unknown")
        val providerPart = provider?.name ?: "N/A"
        val safeDetails = details
            ?.let { sanitize(value = it, maxLength = 200, fallback = "") }
            ?.takeIf { it.isNotBlank() }
        val message = buildString {
            append("stage=")
            append(safeStage)
            append(" provider=")
            append(providerPart)
            if (safeDetails != null) {
                append(' ')
                append(safeDetails)
            }
        }
        logClientAuthEvent(message)
    }

    /** Sanitizes a single client-log field to keep logcat/NSLog output bounded and single-line. */
    private fun sanitize(value: String, maxLength: Int, fallback: String): String {
        val normalized = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(maxLength)
        return normalized.ifBlank { fallback }
    }
}

/** Platform-specific auth log sink used by the shared auth logger. */
internal expect fun logClientAuthEvent(message: String)
