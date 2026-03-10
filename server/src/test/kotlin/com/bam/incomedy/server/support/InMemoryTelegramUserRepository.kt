package com.bam.incomedy.server.support

import com.bam.incomedy.server.auth.telegram.TelegramUser
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.TelegramUserRepository
import java.time.Instant
import java.util.UUID

class InMemoryTelegramUserRepository : TelegramUserRepository {
    private val usersById = linkedMapOf<String, StoredUser>()
    private val usersByTelegramId = linkedMapOf<Long, StoredUser>()
    private val refreshTokens = linkedMapOf<String, RefreshTokenRecord>()
    private val telegramAssertions = linkedMapOf<String, Instant>()

    override fun upsert(user: TelegramUser): StoredUser {
        val existing = usersByTelegramId[user.id]
        val storedUser = StoredUser(
            id = existing?.id ?: UUID.randomUUID().toString(),
            telegramId = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = user.username,
            photoUrl = user.photoUrl,
            sessionRevokedAt = existing?.sessionRevokedAt,
        )
        putUser(storedUser)
        return storedUser
    }

    override fun findById(userId: String): StoredUser? {
        return usersById[userId]
    }

    override fun registerTelegramAuthAssertion(assertionHash: String, telegramUserId: Long, expiresAt: Instant): Boolean {
        val now = Instant.now()
        telegramAssertions.entries.removeIf { (_, expiry) -> expiry <= now }
        return telegramAssertions.putIfAbsent(assertionHash, expiresAt) == null
    }

    override fun revokeSessions(userId: String, revokedAt: Instant) {
        val user = usersById[userId] ?: return
        putUser(user.copy(sessionRevokedAt = revokedAt))
        deleteRefreshTokens(userId)
    }

    override fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant) {
        refreshTokens[tokenHash] = RefreshTokenRecord(userId = userId, expiresAt = expiresAt)
    }

    override fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser? {
        val record = refreshTokens.remove(tokenHash) ?: return null
        if (record.expiresAt <= now) return null
        return usersById[record.userId]
    }

    override fun deleteRefreshTokens(userId: String) {
        refreshTokens.entries.removeIf { (_, record) -> record.userId == userId }
    }

    fun putUser(user: StoredUser) {
        usersById[user.id] = user
        usersByTelegramId[user.telegramId] = user
    }

    private data class RefreshTokenRecord(
        val userId: String,
        val expiresAt: Instant,
    )
}
