package com.bam.incomedy.feature.auth.mvi

import com.bam.incomedy.feature.auth.domain.AuthProviderType

object AuthFlowLogger {
    fun event(
        stage: String,
        provider: AuthProviderType? = null,
        details: String? = null,
    ) {
        val providerPart = provider?.name ?: "N/A"
        val detailsPart = details ?: ""
        println("AUTH_FLOW stage=$stage provider=$providerPart $detailsPart")
    }
}

