package com.bam.incomedy.server.db

import com.bam.incomedy.server.auth.telegram.TelegramUser
import java.time.Instant

enum class AuthProvider(
    val wireName: String,
) {
    PASSWORD("password"),
    PHONE("phone"),
    TELEGRAM("telegram"),
    VK("vk"),
    GOOGLE("google"),
    APPLE("apple"),
    ;

    companion object {
        fun fromWireName(value: String): AuthProvider? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

enum class UserRole(
    val wireName: String,
) {
    AUDIENCE("audience"),
    COMEDIAN("comedian"),
    ORGANIZER("organizer"),
    ;

    companion object {
        fun fromWireName(value: String): UserRole? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

enum class WorkspacePermissionRole(
    val wireName: String,
) {
    OWNER("owner"),
    MANAGER("manager"),
    CHECKER("checker"),
    HOST("host"),
    ;
}

data class StoredUser(
    val id: String,
    val displayName: String,
    val username: String?,
    val photoUrl: String?,
    val sessionRevokedAt: Instant?,
    val linkedProviders: Set<AuthProvider>,
    val roles: Set<UserRole>,
    val activeRole: UserRole?,
)

data class StoredWorkspace(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val permissionRole: WorkspacePermissionRole,
)

data class StoredCredentialAccount(
    val user: StoredUser,
    val login: String,
    val normalizedLogin: String,
    val passwordHash: String,
)

interface UserRepository {
    fun createPasswordIdentity(login: String, normalizedLogin: String, passwordHash: String): StoredUser
    fun findPasswordIdentity(normalizedLogin: String): StoredCredentialAccount?
    fun upsertVkIdentity(
        providerUserId: String,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ): StoredUser
    fun upsertTelegramIdentity(user: TelegramUser): StoredUser
    fun findById(userId: String): StoredUser?
    fun registerTelegramAuthAssertion(assertionHash: String, telegramUserId: Long, expiresAt: Instant): Boolean
    fun revokeSessions(userId: String, revokedAt: Instant)
    fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant)
    fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser?
    fun deleteRefreshTokens(userId: String)
    fun setActiveRole(userId: String, role: UserRole): StoredUser?
    fun createWorkspace(ownerUserId: String, name: String, slug: String): StoredWorkspace
    fun listWorkspaces(userId: String): List<StoredWorkspace>
}

class DuplicateCredentialLoginException(
    val normalizedLogin: String,
) : IllegalStateException("Credential login is already registered")
