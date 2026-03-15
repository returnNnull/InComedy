package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

sealed interface AuthEffect {
    /**
     * Одноразовый эффект, который просит платформенный слой открыть внешний browser-based auth flow.
     *
     * Нативные Android SDK провайдеры завершаются вне этого эффекта и сами передают callback-данные
     * обратно в shared auth-flow.
     */
    data class OpenExternalAuth(
        val provider: AuthProviderType,
        val url: String,
        val state: String = "",
    ) : AuthEffect

    /** One-off effect that invalidates locally stored access/refresh tokens after logout or restore failure. */
    object InvalidateStoredSession : AuthEffect
}
