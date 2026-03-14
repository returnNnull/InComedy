package com.bam.incomedy.server.db

import com.bam.incomedy.server.auth.telegram.TelegramUser
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class PostgresTelegramUserRepository(
    private val dataSource: DataSource,
) : UserRepository {

    override fun createPasswordIdentity(
        login: String,
        normalizedLogin: String,
        passwordHash: String,
    ): StoredUser {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                if (loadCredentialAccount(connection, normalizedLogin) != null) {
                    throw DuplicateCredentialLoginException(normalizedLogin)
                }

                val userId = UUID.randomUUID().toString()
                insertUser(
                    connection = connection,
                    userId = userId,
                    telegramUserId = null,
                    firstName = null,
                    lastName = null,
                    displayName = login,
                    username = login,
                    photoUrl = null,
                )
                upsertPasswordAccount(
                    connection = connection,
                    userId = userId,
                    login = login,
                    normalizedLogin = normalizedLogin,
                    passwordHash = passwordHash,
                )
                upsertAuthIdentity(
                    connection = connection,
                    userId = userId,
                    provider = AuthProvider.PASSWORD,
                    providerUserId = normalizedLogin,
                    username = login,
                )
                ensureRole(connection, userId, UserRole.AUDIENCE)
                ensureActiveRole(connection, userId, UserRole.AUDIENCE)

                val storedUser = loadUserById(connection, userId) ?: error("Failed to load stored user")
                connection.commit()
                return storedUser
            } catch (error: Throwable) {
                connection.rollback()
                if (error.message?.contains("unique", ignoreCase = true) == true) {
                    throw DuplicateCredentialLoginException(normalizedLogin)
                }
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun findPasswordIdentity(normalizedLogin: String): StoredCredentialAccount? {
        dataSource.connection.use { connection ->
            return loadCredentialAccount(connection, normalizedLogin)
        }
    }

    override fun upsertVkIdentity(
        providerUserId: String,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ): StoredUser {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = findUserIdByIdentity(
                    connection = connection,
                    provider = AuthProvider.VK,
                    providerUserId = providerUserId,
                ) ?: UUID.randomUUID().toString().also {
                    insertUser(
                        connection = connection,
                        userId = it,
                        telegramUserId = null,
                        firstName = null,
                        lastName = null,
                        displayName = displayName,
                        username = username,
                        photoUrl = photoUrl,
                    )
                }

                upsertUser(
                    connection = connection,
                    userId = userId,
                    telegramUserId = null,
                    firstName = null,
                    lastName = null,
                    displayName = displayName,
                    username = username,
                    photoUrl = photoUrl,
                )
                upsertAuthIdentity(
                    connection = connection,
                    userId = userId,
                    provider = AuthProvider.VK,
                    providerUserId = providerUserId,
                    username = username,
                )
                ensureRole(connection, userId, UserRole.AUDIENCE)
                ensureActiveRole(connection, userId, UserRole.AUDIENCE)

                val storedUser = loadUserById(connection, userId) ?: error("Failed to load stored user")
                connection.commit()
                return storedUser
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

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

    override fun upsertTelegramIdentity(user: TelegramUser): StoredUser {
        val displayName = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .trim()
            .ifBlank { user.username ?: "Telegram user" }

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val userId = findUserIdByIdentity(
                    connection = connection,
                    provider = AuthProvider.TELEGRAM,
                    providerUserId = user.id.toString(),
                ) ?: findLegacyUserIdByTelegramId(connection, user.id)
                    ?: UUID.randomUUID().toString().also {
                        insertUser(
                            connection = connection,
                            userId = it,
                            telegramUserId = user.id,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            displayName = displayName,
                            username = user.username,
                            photoUrl = user.photoUrl,
                        )
                    }

                upsertUser(
                    connection = connection,
                    userId = userId,
                    telegramUserId = user.id,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    displayName = displayName,
                    username = user.username,
                    photoUrl = user.photoUrl,
                )
                upsertAuthIdentity(
                    connection = connection,
                    userId = userId,
                    provider = AuthProvider.TELEGRAM,
                    providerUserId = user.id.toString(),
                    username = user.username,
                )
                ensureRole(connection, userId, UserRole.AUDIENCE)
                ensureActiveRole(connection, userId, UserRole.AUDIENCE)

                val storedUser = loadUserById(connection, userId) ?: error("Failed to load stored user")
                connection.commit()
                return storedUser
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun findById(userId: String): StoredUser? {
        dataSource.connection.use { connection ->
            return loadUserById(connection, userId)
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
            SELECT c.user_id
            FROM consumed c
            LIMIT 1
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, tokenHash)
                statement.setTimestamp(2, Timestamp.from(now))
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return loadUserById(connection, result.getObject("user_id").toString())
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

    override fun setActiveRole(userId: String, role: UserRole): StoredUser? {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                if (!hasRole(connection, userId, role)) {
                    connection.rollback()
                    return null
                }
                connection.prepareStatement(
                    """
                    UPDATE users
                    SET active_role = ?, updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, role.wireName)
                    statement.setObject(2, UUID.fromString(userId))
                    statement.executeUpdate()
                }
                val storedUser = loadUserById(connection, userId)
                connection.commit()
                return storedUser
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun createWorkspace(ownerUserId: String, name: String, slug: String): StoredWorkspace {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                ensureRole(connection, ownerUserId, UserRole.ORGANIZER)
                val workspaceId = UUID.randomUUID()
                connection.prepareStatement(
                    """
                    INSERT INTO organizer_workspaces (
                        id,
                        owner_user_id,
                        name,
                        slug,
                        status,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, 'active', NOW(), NOW())
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, workspaceId)
                    statement.setObject(2, UUID.fromString(ownerUserId))
                    statement.setString(3, name)
                    statement.setString(4, slug)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    INSERT INTO workspace_members (
                        id,
                        workspace_id,
                        user_id,
                        permission_role,
                        invited_by,
                        joined_at,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                    ON CONFLICT (workspace_id, user_id) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    statement.setObject(1, UUID.randomUUID())
                    statement.setObject(2, workspaceId)
                    statement.setObject(3, UUID.fromString(ownerUserId))
                    statement.setString(4, WorkspacePermissionRole.OWNER.wireName)
                    statement.setObject(5, UUID.fromString(ownerUserId))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    """
                    UPDATE users
                    SET active_role = ?, updated_at = NOW()
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UserRole.ORGANIZER.wireName)
                    statement.setObject(2, UUID.fromString(ownerUserId))
                    statement.executeUpdate()
                }
                connection.commit()
                return StoredWorkspace(
                    id = workspaceId.toString(),
                    name = name,
                    slug = slug,
                    status = "active",
                    permissionRole = WorkspacePermissionRole.OWNER,
                )
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun listWorkspaces(userId: String): List<StoredWorkspace> {
        val sql = """
            SELECT w.id, w.name, w.slug, w.status, wm.permission_role
            FROM organizer_workspaces w
            JOIN workspace_members wm ON wm.workspace_id = w.id
            WHERE wm.user_id = ?
            ORDER BY w.created_at ASC
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, UUID.fromString(userId))
                statement.executeQuery().use { result ->
                    val workspaces = mutableListOf<StoredWorkspace>()
                    while (result.next()) {
                        workspaces += StoredWorkspace(
                            id = result.getObject("id").toString(),
                            name = result.getString("name"),
                            slug = result.getString("slug"),
                            status = result.getString("status"),
                            permissionRole = WorkspacePermissionRole.entries.first {
                                it.wireName == result.getString("permission_role")
                            },
                        )
                    }
                    return workspaces
                }
            }
        }
    }

    private fun findUserIdByIdentity(
        connection: Connection,
        provider: AuthProvider,
        providerUserId: String,
    ): String? {
        connection.prepareStatement(
            """
            SELECT user_id
            FROM auth_identities
            WHERE provider = ? AND provider_user_id = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, provider.wireName)
            statement.setString(2, providerUserId)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                return result.getObject("user_id").toString()
            }
        }
    }

    private fun findLegacyUserIdByTelegramId(connection: Connection, telegramUserId: Long): String? {
        connection.prepareStatement(
            """
            SELECT id
            FROM users
            WHERE telegram_id = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, telegramUserId)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                return result.getObject("id").toString()
            }
        }
    }

    private fun insertUser(
        connection: Connection,
        userId: String,
        telegramUserId: Long?,
        firstName: String?,
        lastName: String?,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO users (
                id,
                telegram_id,
                first_name,
                last_name,
                display_name,
                username,
                photo_url,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.setObject(2, telegramUserId)
            statement.setString(3, firstName)
            statement.setString(4, lastName)
            statement.setString(5, displayName)
            statement.setString(6, username)
            statement.setString(7, photoUrl)
            statement.executeUpdate()
        }
    }

    private fun upsertPasswordAccount(
        connection: Connection,
        userId: String,
        login: String,
        normalizedLogin: String,
        passwordHash: String,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO credential_accounts (
                id,
                user_id,
                login_normalized,
                login_display,
                password_hash,
                password_algorithm,
                password_updated_at,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, 'argon2id', NOW(), NOW(), NOW())
            ON CONFLICT (user_id) DO UPDATE SET
                login_normalized = EXCLUDED.login_normalized,
                login_display = EXCLUDED.login_display,
                password_hash = EXCLUDED.password_hash,
                password_algorithm = EXCLUDED.password_algorithm,
                password_updated_at = NOW(),
                updated_at = NOW()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, UUID.fromString(userId))
            statement.setString(3, normalizedLogin)
            statement.setString(4, login)
            statement.setString(5, passwordHash)
            statement.executeUpdate()
        }
    }

    private fun upsertUser(
        connection: Connection,
        userId: String,
        telegramUserId: Long?,
        firstName: String?,
        lastName: String?,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO users (
                id,
                telegram_id,
                first_name,
                last_name,
                display_name,
                username,
                photo_url,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (id) DO UPDATE SET
                telegram_id = COALESCE(users.telegram_id, EXCLUDED.telegram_id),
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                display_name = EXCLUDED.display_name,
                username = EXCLUDED.username,
                photo_url = EXCLUDED.photo_url,
                updated_at = NOW()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.setObject(2, telegramUserId)
            statement.setString(3, firstName)
            statement.setString(4, lastName)
            statement.setString(5, displayName)
            statement.setString(6, username)
            statement.setString(7, photoUrl)
            statement.executeUpdate()
        }
    }

    private fun upsertAuthIdentity(
        connection: Connection,
        userId: String,
        provider: AuthProvider,
        providerUserId: String,
        username: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO auth_identities (
                id,
                user_id,
                provider,
                provider_user_id,
                username,
                linked_at,
                last_login_at
            ) VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            ON CONFLICT (provider, provider_user_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                username = EXCLUDED.username,
                last_login_at = NOW()
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, UUID.fromString(userId))
            statement.setString(3, provider.wireName)
            statement.setString(4, providerUserId)
            statement.setString(5, username)
            statement.executeUpdate()
        }
    }

    private fun loadCredentialAccount(
        connection: Connection,
        normalizedLogin: String,
    ): StoredCredentialAccount? {
        connection.prepareStatement(
            """
            SELECT user_id, login_display, login_normalized, password_hash
            FROM credential_accounts
            WHERE login_normalized = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, normalizedLogin)
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                val userId = result.getObject("user_id").toString()
                val storedUser = loadUserById(connection, userId) ?: return null
                return StoredCredentialAccount(
                    user = storedUser,
                    login = result.getString("login_display"),
                    normalizedLogin = result.getString("login_normalized"),
                    passwordHash = result.getString("password_hash"),
                )
            }
        }
    }

    private fun ensureRole(connection: Connection, userId: String, role: UserRole) {
        connection.prepareStatement(
            """
            INSERT INTO user_role_assignments (
                id,
                user_id,
                role,
                scope_type,
                scope_id,
                status,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, 'global', 'global', 'active', NOW(), NOW())
            ON CONFLICT (user_id, role, scope_type, scope_id) DO NOTHING
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.randomUUID())
            statement.setObject(2, UUID.fromString(userId))
            statement.setString(3, role.wireName)
            statement.executeUpdate()
        }
    }

    private fun ensureActiveRole(connection: Connection, userId: String, role: UserRole) {
        connection.prepareStatement(
            """
            UPDATE users
            SET active_role = COALESCE(active_role, ?), updated_at = NOW()
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, role.wireName)
            statement.setObject(2, UUID.fromString(userId))
            statement.executeUpdate()
        }
    }

    private fun hasRole(connection: Connection, userId: String, role: UserRole): Boolean {
        connection.prepareStatement(
            """
            SELECT 1
            FROM user_role_assignments
            WHERE user_id = ? AND role = ? AND status = 'active'
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.setString(2, role.wireName)
            statement.executeQuery().use { result ->
                if (result.next()) return true
            }
        }
        if (role == UserRole.AUDIENCE && userHasAnyIdentity(connection, userId)) {
            ensureRole(connection, userId, UserRole.AUDIENCE)
            return true
        }
        return false
    }

    private fun userHasAnyIdentity(connection: Connection, userId: String): Boolean {
        connection.prepareStatement(
            """
            SELECT 1
            FROM auth_identities
            WHERE user_id = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                if (result.next()) return true
            }
        }
        connection.prepareStatement(
            """
            SELECT 1
            FROM users
            WHERE id = ? AND telegram_id IS NOT NULL
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                return result.next()
            }
        }
    }

    private fun loadUserById(connection: Connection, userId: String): StoredUser? {
        val userSql = """
            SELECT id, display_name, username, photo_url, session_revoked_at, active_role
            FROM users
            WHERE id = ?
            LIMIT 1
        """.trimIndent()

        connection.prepareStatement(userSql).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                if (!result.next()) return null
                val linkedProviders = loadLinkedProviders(connection, userId)
                val roles = loadRoles(connection, userId).ifEmpty {
                    if (linkedProviders.isNotEmpty()) linkedSetOf(UserRole.AUDIENCE) else emptySet()
                }
                return StoredUser(
                    id = result.getObject("id").toString(),
                    displayName = result.getString("display_name"),
                    username = result.getString("username"),
                    photoUrl = result.getString("photo_url"),
                    sessionRevokedAt = result.getTimestamp("session_revoked_at")?.toInstant(),
                    linkedProviders = linkedProviders,
                    roles = roles,
                    activeRole = result.getString("active_role")?.let(UserRole::fromWireName) ?: roles.firstOrNull(),
                )
            }
        }
    }

    private fun loadRoles(connection: Connection, userId: String): Set<UserRole> {
        connection.prepareStatement(
            """
            SELECT role
            FROM user_role_assignments
            WHERE user_id = ? AND status = 'active'
            ORDER BY role ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                val roles = linkedSetOf<UserRole>()
                while (result.next()) {
                    UserRole.fromWireName(result.getString("role"))?.let(roles::add)
                }
                return roles
            }
        }
    }

    private fun loadLinkedProviders(connection: Connection, userId: String): Set<AuthProvider> {
        connection.prepareStatement(
            """
            SELECT provider
            FROM auth_identities
            WHERE user_id = ?
            ORDER BY provider ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, UUID.fromString(userId))
            statement.executeQuery().use { result ->
                val providers = linkedSetOf<AuthProvider>()
                while (result.next()) {
                    AuthProvider.fromWireName(result.getString("provider"))?.let(providers::add)
                }
                return providers
            }
        }
    }
}
