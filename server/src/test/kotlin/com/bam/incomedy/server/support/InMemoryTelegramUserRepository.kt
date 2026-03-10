package com.bam.incomedy.server.support

import com.bam.incomedy.server.auth.telegram.TelegramUser
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.StoredWorkspace
import com.bam.incomedy.server.db.UserRepository
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.db.WorkspacePermissionRole
import java.time.Instant
import java.util.UUID

class InMemoryTelegramUserRepository : UserRepository {
    private val usersById = linkedMapOf<String, MutableStoredUser>()
    private val usersByTelegramId = linkedMapOf<Long, String>()
    private val refreshTokens = linkedMapOf<String, RefreshTokenRecord>()
    private val telegramAssertions = linkedMapOf<String, Instant>()
    private val workspacesById = linkedMapOf<String, StoredWorkspaceRecord>()
    private val workspaceIdsByUserId = linkedMapOf<String, MutableList<String>>()

    override fun upsertTelegramIdentity(user: TelegramUser): StoredUser {
        val existingUserId = usersByTelegramId[user.id]
        val mutableUser = existingUserId
            ?.let(usersById::getValue)
            ?: MutableStoredUser(
                id = UUID.randomUUID().toString(),
                displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim().ifBlank {
                    user.username ?: "Telegram user"
                },
                username = user.username,
                photoUrl = user.photoUrl,
                sessionRevokedAt = null,
                linkedProviders = linkedSetOf(AuthProvider.TELEGRAM),
                roles = linkedSetOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            )
        mutableUser.displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim().ifBlank {
            user.username ?: mutableUser.displayName
        }
        mutableUser.username = user.username
        mutableUser.photoUrl = user.photoUrl
        mutableUser.linkedProviders += AuthProvider.TELEGRAM
        mutableUser.roles += UserRole.AUDIENCE
        if (mutableUser.activeRole == null) {
            mutableUser.activeRole = UserRole.AUDIENCE
        }
        usersById[mutableUser.id] = mutableUser
        usersByTelegramId[user.id] = mutableUser.id
        return mutableUser.toStored()
    }

    override fun findById(userId: String): StoredUser? {
        return usersById[userId]?.toStored()
    }

    override fun registerTelegramAuthAssertion(assertionHash: String, telegramUserId: Long, expiresAt: Instant): Boolean {
        val now = Instant.now()
        telegramAssertions.entries.removeIf { (_, expiry) -> expiry <= now }
        return telegramAssertions.putIfAbsent(assertionHash, expiresAt) == null
    }

    override fun revokeSessions(userId: String, revokedAt: Instant) {
        val user = usersById[userId] ?: return
        user.sessionRevokedAt = revokedAt
        deleteRefreshTokens(userId)
    }

    override fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant) {
        refreshTokens[tokenHash] = RefreshTokenRecord(userId = userId, expiresAt = expiresAt)
    }

    override fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser? {
        val record = refreshTokens.remove(tokenHash) ?: return null
        if (record.expiresAt <= now) return null
        return usersById[record.userId]?.toStored()
    }

    override fun deleteRefreshTokens(userId: String) {
        refreshTokens.entries.removeIf { (_, record) -> record.userId == userId }
    }

    override fun setActiveRole(userId: String, role: UserRole): StoredUser? {
        val user = usersById[userId] ?: return null
        if (!user.roles.contains(role)) return null
        user.activeRole = role
        return user.toStored()
    }

    override fun createWorkspace(ownerUserId: String, name: String, slug: String): StoredWorkspace {
        val user = usersById[ownerUserId] ?: error("User not found")
        user.roles += UserRole.ORGANIZER
        user.activeRole = UserRole.ORGANIZER
        val workspace = StoredWorkspaceRecord(
            id = UUID.randomUUID().toString(),
            name = name,
            slug = slug,
            status = "active",
            permissionRole = WorkspacePermissionRole.OWNER,
        )
        workspacesById[workspace.id] = workspace
        workspaceIdsByUserId.getOrPut(ownerUserId) { mutableListOf() }.add(workspace.id)
        return workspace.toStored()
    }

    override fun listWorkspaces(userId: String): List<StoredWorkspace> {
        return workspaceIdsByUserId[userId]
            .orEmpty()
            .mapNotNull(workspacesById::get)
            .map(StoredWorkspaceRecord::toStored)
    }

    fun putUser(user: StoredUser) {
        usersById[user.id] = MutableStoredUser(
            id = user.id,
            displayName = user.displayName,
            username = user.username,
            photoUrl = user.photoUrl,
            sessionRevokedAt = user.sessionRevokedAt,
            linkedProviders = user.linkedProviders.toMutableSet(),
            roles = user.roles.toMutableSet(),
            activeRole = user.activeRole,
        )
    }

    private data class RefreshTokenRecord(
        val userId: String,
        val expiresAt: Instant,
    )

    private data class MutableStoredUser(
        val id: String,
        var displayName: String,
        var username: String?,
        var photoUrl: String?,
        var sessionRevokedAt: Instant?,
        val linkedProviders: MutableSet<AuthProvider>,
        val roles: MutableSet<UserRole>,
        var activeRole: UserRole?,
    ) {
        fun toStored(): StoredUser {
            return StoredUser(
                id = id,
                displayName = displayName,
                username = username,
                photoUrl = photoUrl,
                sessionRevokedAt = sessionRevokedAt,
                linkedProviders = linkedProviders.toSet(),
                roles = roles.toSet(),
                activeRole = activeRole,
            )
        }
    }

    private data class StoredWorkspaceRecord(
        val id: String,
        val name: String,
        val slug: String,
        val status: String,
        val permissionRole: WorkspacePermissionRole,
    ) {
        fun toStored(): StoredWorkspace {
            return StoredWorkspace(
                id = id,
                name = name,
                slug = slug,
                status = status,
                permissionRole = permissionRole,
            )
        }
    }
}
