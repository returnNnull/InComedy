package com.bam.incomedy.server.auth.session

import com.bam.incomedy.server.db.AuthProvider

interface SessionTokenService {
    fun issue(userId: String, provider: AuthProvider): SessionTokens
}
