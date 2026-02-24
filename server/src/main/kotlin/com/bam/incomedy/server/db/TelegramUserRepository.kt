package com.bam.incomedy.server.db

import com.bam.incomedy.server.auth.telegram.TelegramUser
import java.time.Instant

data class StoredUser(
    val id: String,
    val telegramId: Long,
    val firstName: String,
    val lastName: String?,
    val username: String?,
    val photoUrl: String?,
)

interface TelegramUserRepository {
    fun upsert(user: TelegramUser): StoredUser
    fun findById(userId: String): StoredUser?
    fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant)
}
