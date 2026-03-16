package com.bam.incomedy.feature.venue

/**
 * Одноразовые эффекты venue management feature.
 */
sealed interface VenueEffect {
    /**
     * Сигнализирует, что мутация завершилась успешно и UI может показать toast/snackbar.
     *
     * @property message Краткое безопасное сообщение для пользователя.
     */
    data class MutationCompleted(
        val message: String,
    ) : VenueEffect
}
