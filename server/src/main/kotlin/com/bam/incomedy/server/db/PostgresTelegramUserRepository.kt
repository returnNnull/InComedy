package com.bam.incomedy.server.db

import com.bam.incomedy.server.auth.telegram.TelegramUser
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresTelegramUserRepository(
    private val dataSource: DataSource,
) : TelegramUserRepository {

    override fun registerTelegramAuthAssertion(
        assertionHash: String,
        telegramUserId: Long,
        expiresAt: Instant,
    ): Boolean {
        val cleanupSql = """
            DELETE FROM telegram_auth_assertions
            WHERE expires_at <= NOW()
        """.trimIndent()
        val insertSql = """
            INSERT INTO telegram_auth_assertions (hash, telegram_id, expires_at, created_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (hash) DO NOTHING
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(cleanupSql).use { it.executeUpdate() }
                val inserted = connection.prepareStatement(insertSql).use { statement ->
                    statement.setString(1, assertionHash)
                    statement.setLong(2, telegramUserId)
                    statement.setTimestamp(3, Timestamp.from(expiresAt))
                    statement.executeUpdate() == 1
                }
                connection.commit()
                return inserted
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
    }

    override fun upsert(user: TelegramUser): StoredUser {
        val generatedId = UUID.randomUUID()
        val sql = """
            INSERT INTO users (
                id,
                telegram_id,
                first_name,
                last_name,
                username,
                photo_url,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (telegram_id) DO UPDATE SET
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                username = EXCLUDED.username,
                photo_url = EXCLUDED.photo_url,
                updated_at = NOW()
            RETURNING id, telegram_id, first_name, last_name, username, photo_url, session_revoked_at
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, generatedId)
                statement.setLong(2, user.id)
                statement.setString(3, user.firstName)
                statement.setString(4, user.lastName)
                statement.setString(5, user.username)
                statement.setString(6, user.photoUrl)
                statement.executeQuery().use { result ->
                    if (!result.next()) error("Failed to upsert telegram user")
                    return StoredUser(
                        id = result.getObject("id").toString(),
                        telegramId = result.getLong("telegram_id"),
                        firstName = result.getString("first_name"),
                        lastName = result.getString("last_name"),
                        username = result.getString("username"),
                        photoUrl = result.getString("photo_url"),
                        sessionRevokedAt = result.getTimestamp("session_revoked_at")?.toInstant(),
                    )
                }
            }
        }
    }

    override fun findById(userId: String): StoredUser? {
        val sql = """
            SELECT id, telegram_id, first_name, last_name, username, photo_url, session_revoked_at
            FROM users
            WHERE id = ?
            LIMIT 1
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return StoredUser(
                        id = result.getObject("id").toString(),
                        telegramId = result.getLong("telegram_id"),
                        firstName = result.getString("first_name"),
                        lastName = result.getString("last_name"),
                        username = result.getString("username"),
                        photoUrl = result.getString("photo_url"),
                        sessionRevokedAt = result.getTimestamp("session_revoked_at")?.toInstant(),
                    )
                }
            }
        }
    }

    override fun revokeSessions(userId: String, revokedAt: Instant) {
        val sql = """
            UPDATE users
            SET session_revoked_at = ?
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setTimestamp(1, Timestamp.from(revokedAt))
                statement.setObject(2, UUID.fromString(userId))
                statement.executeUpdate()
            }
        }
        deleteRefreshTokens(userId)
    }

    override fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant) {
        val sql = """
            INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, NOW())
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, UUID.fromString(userId))
                statement.setString(3, tokenHash)
                statement.setTimestamp(4, Timestamp.from(expiresAt))
                statement.executeUpdate()
            }
        }
    }

    override fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser? {
        val sql = """
            WITH consumed AS (
                DELETE FROM refresh_tokens
                WHERE token_hash = ? AND expires_at > ?
                RETURNING user_id
            )
            SELECT u.id, u.telegram_id, u.first_name, u.last_name, u.username, u.photo_url, u.session_revoked_at
            FROM users u
            JOIN consumed c ON c.user_id = u.id
            LIMIT 1
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, tokenHash)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return StoredUser(
                        id = result.getObject("id").toString(),
                        telegramId = result.getLong("telegram_id"),
                        firstName = result.getString("first_name"),
                        lastName = result.getString("last_name"),
                        username = result.getString("username"),
                        photoUrl = result.getString("photo_url"),
                        sessionRevokedAt = result.getTimestamp("session_revoked_at")?.toInstant(),
                    )
                }
            }
        }
    }

    override fun deleteRefreshTokens(userId: String) {
        val sql = """
            DELETE FROM refresh_tokens
            WHERE user_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeUpdate()
            }
        }
    }
}
