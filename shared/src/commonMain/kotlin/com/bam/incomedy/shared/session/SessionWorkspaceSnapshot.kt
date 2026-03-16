package com.bam.incomedy.shared.session

/**
 * Bridge-представление рабочего пространства для iOS-слоя.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль пользователя внутри рабочего пространства.
 * @property canManageMembers Показывает, может ли viewer управлять командой workspace.
 * @property assignablePermissionRoles Роли, которые viewer может назначать при создании invite.
 * @property memberships Экспортируемый roster workspace для iOS-слоя.
 */
data class SessionWorkspaceSnapshot(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val permissionRole: String,
    val canManageMembers: Boolean,
    val assignablePermissionRoles: List<String>,
    val memberships: List<SessionWorkspaceMembershipSnapshot>,
)
