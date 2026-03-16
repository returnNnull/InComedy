package com.bam.incomedy.shared.session

/**
 * Bridge-представление membership записи organizer workspace для iOS-слоя.
 *
 * @property membershipId Идентификатор membership/invitation записи.
 * @property userId Идентификатор пользователя.
 * @property displayName Отображаемое имя участника.
 * @property username Username участника, если он известен.
 * @property permissionRole Permission role внутри workspace.
 * @property status Состояние membership: `active` или `invited`.
 * @property invitedByDisplayName Имя инициатора invite, если запись pending.
 * @property isCurrentUser Показывает, что membership принадлежит текущей сессии.
 * @property canEditRole Показывает, разрешено ли менять role этой записи.
 * @property assignablePermissionRoles Список ролей, которые можно назначить этой записи.
 */
data class SessionWorkspaceMembershipSnapshot(
    val membershipId: String,
    val userId: String,
    val displayName: String,
    val username: String?,
    val permissionRole: String,
    val status: String,
    val invitedByDisplayName: String?,
    val isCurrentUser: Boolean,
    val canEditRole: Boolean,
    val assignablePermissionRoles: List<String>,
)
