package com.bam.incomedy.server.auth.session

interface SessionTokenService {
    fun issue(userId: String, telegramUserId: Long): SessionTokens
}

