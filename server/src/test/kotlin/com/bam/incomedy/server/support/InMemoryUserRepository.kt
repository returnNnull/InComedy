package com.bam.incomedy.server.support

import com.bam.incomedy.server.auth.telegram.TelegramUser
import com.bam.incomedy.server.db.AuthProvider
import com.bam.incomedy.server.db.DuplicateCredentialLoginException
import com.bam.incomedy.server.db.StoredCredentialAccount
import com.bam.incomedy.server.db.StoredUser
import com.bam.incomedy.server.db.StoredWorkspace
import com.bam.incomedy.server.db.StoredWorkspaceAccess
import com.bam.incomedy.server.db.StoredWorkspaceInvitation
import com.bam.incomedy.server.db.StoredWorkspaceMembership
import com.bam.incomedy.server.db.UserRepository
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.server.db.WorkspaceInviteeNotFoundException
import com.bam.incomedy.server.db.WorkspaceMembershipAlreadyExistsException
import com.bam.incomedy.server.db.WorkspaceMembershipStatus
import com.bam.incomedy.server.db.WorkspacePermissionRole
import java.time.Instant
import java.util.UUID

/**
 * In-memory реализация `UserRepository` для серверных тестов auth/workspace сценариев.
 *
 * Хранилище поддерживает роли, рабочие пространства и pending invitations без обращения к БД,
 * чтобы route/service тесты могли проверять бизнес-правила детерминированно.
 */
class InMemoryUserRepository : UserRepository {
    /** Пользователи по внутреннему идентификатору. */
    private val usersById = linkedMapOf<String, MutableStoredUser>()

    /** Индекс credential login -> user id для auth и team invitation lookup. */
    private val usersByNormalizedLogin = linkedMapOf<String, String>()

    /** Индекс Telegram id -> user id для legacy auth тестов. */
    private val usersByTelegramId = linkedMapOf<Long, String>()

    /** Индекс VK id -> user id для VK auth тестов. */
    private val usersByVkId = linkedMapOf<String, String>()

    /** Password hashes по user id для credential auth тестов. */
    private val passwordHashesByUserId = linkedMapOf<String, String>()

    /** Refresh tokens in-memory. */
    private val refreshTokens = linkedMapOf<String, RefreshTokenRecord>()

    /** Telegram auth assertions in-memory. */
    private val telegramAssertions = linkedMapOf<String, Instant>()

    /** Рабочие пространства по их id. */
    private val workspacesById = linkedMapOf<String, StoredWorkspaceRecord>()

    /** Membership records по их id. */
    private val membershipsById = linkedMapOf<String, StoredWorkspaceMembershipRecord>()

    /** Упорядоченный индекс membership ids внутри workspace. */
    private val membershipIdsByWorkspaceId = linkedMapOf<String, MutableList<String>>()

    /** Упорядоченный индекс membership ids пользователя. */
    private val membershipIdsByUserId = linkedMapOf<String, MutableList<String>>()

    /** Создает password identity и audience-базу для тестового пользователя. */
    override fun createPasswordIdentity(login: String, normalizedLogin: String, passwordHash: String): StoredUser {
        if (usersByNormalizedLogin.containsKey(normalizedLogin)) {
            throw DuplicateCredentialLoginException(normalizedLogin)
        }
        val mutableUser = MutableStoredUser(
            id = UUID.randomUUID().toString(),
            displayName = login,
            username = login,
            photoUrl = null,
            sessionRevokedAt = null,
            linkedProviders = linkedSetOf(AuthProvider.PASSWORD),
            roles = linkedSetOf(UserRole.AUDIENCE),
            activeRole = UserRole.AUDIENCE,
        )
        usersById[mutableUser.id] = mutableUser
        usersByNormalizedLogin[normalizedLogin] = mutableUser.id
        passwordHashesByUserId[mutableUser.id] = passwordHash
        return mutableUser.toStored()
    }

    /** Возвращает credential account по normalized login. */
    override fun findPasswordIdentity(normalizedLogin: String): StoredCredentialAccount? {
        val userId = usersByNormalizedLogin[normalizedLogin] ?: return null
        val user = usersById[userId]?.toStored() ?: return null
        val passwordHash = passwordHashesByUserId[userId] ?: return null
        return StoredCredentialAccount(
            user = user,
            login = user.username ?: user.displayName,
            normalizedLogin = normalizedLogin,
            passwordHash = passwordHash,
        )
    }

    /** Создает или обновляет VK identity для тестов VK auth. */
    override fun upsertVkIdentity(
        providerUserId: String,
        displayName: String,
        username: String?,
        photoUrl: String?,
    ): StoredUser {
        val existingUserId = usersByVkId[providerUserId]
        val mutableUser = existingUserId
            ?.let(usersById::getValue)
            ?: MutableStoredUser(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                username = username,
                photoUrl = photoUrl,
                sessionRevokedAt = null,
                linkedProviders = linkedSetOf(AuthProvider.VK),
                roles = linkedSetOf(UserRole.AUDIENCE),
                activeRole = UserRole.AUDIENCE,
            )
        mutableUser.displayName = displayName
        mutableUser.username = username
        mutableUser.photoUrl = photoUrl
        mutableUser.linkedProviders += AuthProvider.VK
        mutableUser.roles += UserRole.AUDIENCE
        if (mutableUser.activeRole == null) {
            mutableUser.activeRole = UserRole.AUDIENCE
        }
        usersById[mutableUser.id] = mutableUser
        usersByVkId[providerUserId] = mutableUser.id
        return mutableUser.toStored()
    }

    /** Создает или обновляет Telegram identity для legacy auth тестов. */
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

    /** Загружает пользователя по id. */
    override fun findById(userId: String): StoredUser? {
        return usersById[userId]?.toStored()
    }

    /** Регистрирует Telegram assertion для replay-защиты тестов. */
    override fun registerTelegramAuthAssertion(assertionHash: String, telegramUserId: Long, expiresAt: Instant): Boolean {
        val now = Instant.now()
        telegramAssertions.entries.removeIf { (_, expiry) -> expiry <= now }
        return telegramAssertions.putIfAbsent(assertionHash, expiresAt) == null
    }

    /** Отзывает все сессии пользователя и очищает refresh tokens. */
    override fun revokeSessions(userId: String, revokedAt: Instant) {
        val user = usersById[userId] ?: return
        user.sessionRevokedAt = revokedAt
        deleteRefreshTokens(userId)
    }

    /** Сохраняет refresh token в тестовом хранилище. */
    override fun storeRefreshToken(userId: String, tokenHash: String, expiresAt: Instant) {
        refreshTokens[tokenHash] = RefreshTokenRecord(userId = userId, expiresAt = expiresAt)
    }

    /** Однократно потребляет refresh token. */
    override fun consumeRefreshToken(tokenHash: String, now: Instant): StoredUser? {
        val record = refreshTokens.remove(tokenHash) ?: return null
        if (record.expiresAt <= now) return null
        return usersById[record.userId]?.toStored()
    }

    /** Удаляет все refresh tokens пользователя. */
    override fun deleteRefreshTokens(userId: String) {
        refreshTokens.entries.removeIf { (_, record) -> record.userId == userId }
    }

    /** Меняет активную глобальную роль, если она уже назначена пользователю. */
    override fun setActiveRole(userId: String, role: UserRole): StoredUser? {
        val user = usersById[userId] ?: return null
        if (!user.roles.contains(role)) return null
        user.activeRole = role
        return user.toStored()
    }

    /** Создает workspace и сразу активное owner membership. */
    override fun createWorkspace(ownerUserId: String, name: String, slug: String): StoredWorkspace {
        val user = usersById[ownerUserId] ?: error("User not found")
        user.roles += UserRole.ORGANIZER
        user.activeRole = UserRole.ORGANIZER

        val workspace = StoredWorkspaceRecord(
            id = UUID.randomUUID().toString(),
            name = name,
            slug = slug,
            status = "active",
        )
        workspacesById[workspace.id] = workspace
        addMembership(
            workspaceId = workspace.id,
            userId = ownerUserId,
            permissionRole = WorkspacePermissionRole.OWNER,
            status = WorkspaceMembershipStatus.ACTIVE,
            invitedByUserId = ownerUserId,
        )
        return workspace.toStored(
            viewerPermissionRole = WorkspacePermissionRole.OWNER,
            memberships = loadWorkspaceMemberships(workspace.id),
        )
    }

    /** Возвращает активные workspace пользователя вместе с roster/pending invites. */
    override fun listWorkspaces(userId: String): List<StoredWorkspace> {
        return membershipsForUser(userId)
            .filter { it.status == WorkspaceMembershipStatus.ACTIVE }
            .mapNotNull { membership ->
                val workspace = workspacesById[membership.workspaceId] ?: return@mapNotNull null
                workspace.toStored(
                    viewerPermissionRole = membership.permissionRole,
                    memberships = loadWorkspaceMemberships(workspace.id),
                )
            }
    }

    /** Возвращает pending invitations текущего пользователя. */
    override fun listWorkspaceInvitations(userId: String): List<StoredWorkspaceInvitation> {
        return membershipsForUser(userId)
            .filter { it.status == WorkspaceMembershipStatus.INVITED }
            .mapNotNull { membership ->
                val workspace = workspacesById[membership.workspaceId] ?: return@mapNotNull null
                StoredWorkspaceInvitation(
                    membershipId = membership.membershipId,
                    workspaceId = workspace.id,
                    workspaceName = workspace.name,
                    workspaceSlug = workspace.slug,
                    workspaceStatus = workspace.status,
                    permissionRole = membership.permissionRole,
                    invitedByDisplayName = membership.invitedByUserId
                        ?.let(usersById::get)
                        ?.displayName,
                )
            }
    }

    /** Возвращает активный доступ пользователя к workspace. */
    override fun findWorkspaceAccess(workspaceId: String, userId: String): StoredWorkspaceAccess? {
        val membership = membershipsForUser(userId).firstOrNull {
            it.workspaceId == workspaceId && it.status == WorkspaceMembershipStatus.ACTIVE
        } ?: return null
        return StoredWorkspaceAccess(
            workspaceId = workspaceId,
            membershipId = membership.membershipId,
            permissionRole = membership.permissionRole,
        )
    }

    /** Возвращает membership записи workspace по membership id. */
    override fun findWorkspaceMembership(workspaceId: String, membershipId: String): StoredWorkspaceMembership? {
        val membership = membershipsById[membershipId] ?: return null
        if (membership.workspaceId != workspaceId) return null
        return membership.toStored(usersById)
    }

    /** Создает pending invitation для уже зарегистрированного пользователя. */
    override fun createWorkspaceInvitation(
        workspaceId: String,
        invitedByUserId: String,
        inviteeIdentifier: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership {
        val inviteeUserId = resolveInviteeUserId(inviteeIdentifier)
            ?: throw WorkspaceInviteeNotFoundException(inviteeIdentifier)

        val existingMembership = membershipsForUser(inviteeUserId).firstOrNull { it.workspaceId == workspaceId }
        if (existingMembership != null) {
            throw WorkspaceMembershipAlreadyExistsException(
                workspaceId = workspaceId,
                userId = inviteeUserId,
                pending = existingMembership.status == WorkspaceMembershipStatus.INVITED,
            )
        }

        return addMembership(
            workspaceId = workspaceId,
            userId = inviteeUserId,
            permissionRole = permissionRole,
            status = WorkspaceMembershipStatus.INVITED,
            invitedByUserId = invitedByUserId,
        ).toStored(usersById)
    }

    /** Меняет permission role у active/pending membership. */
    override fun updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: WorkspacePermissionRole,
    ): StoredWorkspaceMembership? {
        val membership = membershipsById[membershipId] ?: return null
        if (membership.workspaceId != workspaceId) return null
        membership.permissionRole = permissionRole
        return membership.toStored(usersById)
    }

    /** Принимает или отклоняет pending invitation пользователя. */
    override fun respondToWorkspaceInvitation(
        userId: String,
        membershipId: String,
        accept: Boolean,
    ): Boolean {
        val membership = membershipsById[membershipId] ?: return false
        if (membership.userId != userId || membership.status != WorkspaceMembershipStatus.INVITED) {
            return false
        }
        return if (accept) {
            val user = usersById[userId] ?: return false
            user.roles += UserRole.ORGANIZER
            membership.status = WorkspaceMembershipStatus.ACTIVE
            true
        } else {
            removeMembership(membershipId)
            true
        }
    }

    /** Подкладывает готового пользователя в in-memory store для route тестов. */
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
        if (user.linkedProviders.contains(AuthProvider.PASSWORD)) {
            user.username?.lowercase()?.let { usersByNormalizedLogin[it] = user.id }
            passwordHashesByUserId.putIfAbsent(user.id, "test-password-hash")
        }
    }

    /** Возвращает memberships пользователя в порядке вставки. */
    private fun membershipsForUser(userId: String): List<StoredWorkspaceMembershipRecord> {
        return membershipIdsByUserId[userId]
            .orEmpty()
            .mapNotNull(membershipsById::get)
    }

    /** Возвращает полный roster workspace в порядке active -> pending. */
    private fun loadWorkspaceMemberships(workspaceId: String): List<StoredWorkspaceMembership> {
        return membershipIdsByWorkspaceId[workspaceId]
            .orEmpty()
            .mapNotNull(membershipsById::get)
            .sortedBy { if (it.status == WorkspaceMembershipStatus.ACTIVE) 0 else 1 }
            .map { it.toStored(usersById) }
    }

    /** Находит пользователя по login/username для invitation flow. */
    private fun resolveInviteeUserId(inviteeIdentifier: String): String? {
        val normalizedIdentifier = inviteeIdentifier.trim().lowercase()
        if (normalizedIdentifier.isBlank()) return null
        usersByNormalizedLogin[normalizedIdentifier]?.let { return it }
        return usersById.values.firstOrNull { it.username?.lowercase() == normalizedIdentifier }?.id
    }

    /** Создает membership, обновляя оба индекса workspace/user. */
    private fun addMembership(
        workspaceId: String,
        userId: String,
        permissionRole: WorkspacePermissionRole,
        status: WorkspaceMembershipStatus,
        invitedByUserId: String?,
    ): StoredWorkspaceMembershipRecord {
        val membership = StoredWorkspaceMembershipRecord(
            membershipId = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            userId = userId,
            permissionRole = permissionRole,
            status = status,
            invitedByUserId = invitedByUserId,
        )
        membershipsById[membership.membershipId] = membership
        membershipIdsByWorkspaceId.getOrPut(workspaceId) { mutableListOf() }.add(membership.membershipId)
        membershipIdsByUserId.getOrPut(userId) { mutableListOf() }.add(membership.membershipId)
        return membership
    }

    /** Полностью удаляет membership из store и вспомогательных индексов. */
    private fun removeMembership(membershipId: String) {
        val membership = membershipsById.remove(membershipId) ?: return
        membershipIdsByWorkspaceId[membership.workspaceId]?.remove(membershipId)
        membershipIdsByUserId[membership.userId]?.remove(membershipId)
    }

    /** Запись refresh token в тестовом хранилище. */
    private data class RefreshTokenRecord(
        val userId: String,
        val expiresAt: Instant,
    )

    /** Mutable-представление пользователя для in-memory обновлений. */
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
        /** Экспортирует mutable user в immutable server model. */
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

    /** Mutable-запись рабочего пространства без viewer-specific полей. */
    private data class StoredWorkspaceRecord(
        val id: String,
        val name: String,
        val slug: String,
        val status: String,
    ) {
        /** Экспортирует workspace в серверную модель с viewer permission role и roster. */
        fun toStored(
            viewerPermissionRole: WorkspacePermissionRole,
            memberships: List<StoredWorkspaceMembership>,
        ): StoredWorkspace {
            return StoredWorkspace(
                id = id,
                name = name,
                slug = slug,
                status = status,
                permissionRole = viewerPermissionRole,
                memberships = memberships,
            )
        }
    }

    /** Mutable-запись membership/invitation внутри workspace. */
    private data class StoredWorkspaceMembershipRecord(
        val membershipId: String,
        val workspaceId: String,
        val userId: String,
        var permissionRole: WorkspacePermissionRole,
        var status: WorkspaceMembershipStatus,
        val invitedByUserId: String?,
    ) {
        /** Преобразует membership в ответную модель репозитория. */
        fun toStored(usersById: Map<String, MutableStoredUser>): StoredWorkspaceMembership {
            return StoredWorkspaceMembership(
                membershipId = membershipId,
                userId = userId,
                displayName = usersById.getValue(userId).displayName,
                username = usersById.getValue(userId).username,
                permissionRole = permissionRole,
                status = status,
                invitedByDisplayName = invitedByUserId?.let(usersById::get)?.displayName,
            )
        }
    }
}
