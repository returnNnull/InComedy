package com.bam.incomedy.feature.event

/**
 * Одноразовые эффекты organizer event feature.
 */
sealed interface EventEffect {
    /**
     * Сигнализирует об успешной event mutation.
     *
     * @property message Безопасное сообщение для toast/snackbar.
     */
    data class MutationCompleted(
        val message: String,
    ) : EventEffect
}
