package com.bam.incomedy.feature.auth.domain

interface SessionTerminationService {
    suspend fun terminate(accessToken: String): Result<Unit>
}
