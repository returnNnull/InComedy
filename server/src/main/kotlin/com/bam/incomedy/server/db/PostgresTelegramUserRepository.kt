package com.bam.incomedy.server.db

import com.bam.incomedy.server.auth.telegram.TelegramUser
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresTelegramUserRepository(
    private val dataSource: DataSource,
) : TelegramUserRepository {

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
            RETURNING id, telegram_id, first_name, last_name, username, photo_url
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
                    )
                }
            }
        }
    }

    override fun findById(userId: String): StoredUser? {
        val sql = """
            SELECT id, telegram_id, first_name, last_name, username, photo_url
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
                    )
                }
            }
        }
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
}
