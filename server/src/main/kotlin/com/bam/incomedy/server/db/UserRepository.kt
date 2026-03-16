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

    companion object {
        fun fromWireName(value: String): WorkspacePermissionRole? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

enum class WorkspaceMembershipStatus(
    val wireName: String,
) {
    INVITED("invited"),
    ACTIVE("active"),
    ;

    companion object {
        fun fromJoined(joined: Boolean): WorkspaceMembershipStatus {
            return if (joined) ACTIVE else INVITED
        }
    }
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
    val memberships: List<StoredWorkspaceMembership> = emptyList(),
)

data class StoredWorkspaceMembership(
    val membershipId: String,
    val userId: String,
    val displayName: String,
    val username: String?,
    val permissionRole: WorkspacePermissionRole,
    val status: WorkspaceMembershipStatus,
    val invitedByDisplayName: String? = null,
)

data class StoredWorkspaceInvitation(
    val membershipId: String,
    val workspaceId: String,
    val workspaceName: String,
    val workspaceSlug: String,
    val workspaceStatus: String,
    val permissionRole: WorkspacePermissionRole,
    val invitedByDisplayName: String? = null,
)

data class StoredWorkspaceAccess(
    val workspaceId: String,
    val membershipId: String,
    val permissionRole: WorkspacePermissionRole,
)

data class StoredCredentialAccount(
    val user: StoredUser,
    val login: String,
    val normalizedLogin: String,
    val passwordHash: String,
)

/**
 * Минимальный port для session lifecycle и role context.
 *
 * Этот контракт нужен auth/session слоям, которым не должны быть видны organizer workspace детали.
 */
interface SessionUserRepository {
    fun findById(userId: String): StoredUser?
    fun revokeSessions(userId: String, revokedAt: Instant)
    fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant)
    fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser?
    fun deleteRefreshTokens(userId: String)
    fun setActiveRole(userId: String, role: UserRole): StoredUser?
}

/**
 * Минимальный port organizer workspace bounded context-а.
 *
 * Контракт выделяет workspace/membership операции из общего persistence слоя, чтобы organizer код
 * не зависел от credential/provider auth API.
 */
interface WorkspaceRepository {
    fun createWorkspace(ownerUserId: String, name: String, slug: String): StoredWorkspace
    fun listWorkspaces(userId: String): List<StoredWorkspace>
    fun listWorkspaceInvitations(userId: String): List<StoredWorkspaceInvitation>
    fun findWorkspaceAccess(workspaceId: String, userId: String): StoredWorkspaceAccess?
    fun findWorkspaceMembership(workspaceId: String, membershipId: String): StoredWorkspaceMembership?
    fun createWorkspaceInvitation(
        workspaceId: String,
        invitedByUserId: String,
        inviteeIdentifier: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership

    fun updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership?

    fun respondToWorkspaceInvitation(
        userId: String,
        membershipId: String,
        accept: Boolean,
    ): Boolean
}

/**
 * Полный persistence-контракт backend-а.
 *
 * Исторически это один крупный репозиторий, но поверх него допускаются более узкие порты
 * (`SessionUserRepository`, `WorkspaceRepository`) для bounded context-слоев.
 */
interface UserRepository : SessionUserRepository, WorkspaceRepository {
    fun createPasswordIdentity(login: String, normalizedLogin: String, passwordHash: String): StoredUser
    fun findPasswordIdentity(normalizedLogin: String): StoredCredentialAccount?
    fun upsertVkIdentity(
        providerUserId: String,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ): StoredUser

    fun upsertTelegramIdentity(user: TelegramUser): StoredUser
    fun registerTelegramAuthAssertion(assertionHash: String, telegramUserId: Long, expiresAt: Instant): Boolean
}

class DuplicateCredentialLoginException(
    val normalizedLogin: String,
) : IllegalStateException("Credential login is already registered")

class WorkspaceInviteeNotFoundException(
    val inviteeIdentifier: String,
) : IllegalStateException("Workspace invitee was not found")

class WorkspaceMembershipAlreadyExistsException(
    val workspaceId: String,
    val userId: String,
    val pending: Boolean,
) : IllegalStateException("Workspace membership already exists")
