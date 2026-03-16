package com.bam.incomedy.shared.session

/**
 * Bridge-представление pending invitation текущего пользователя для iOS-слоя.
 *
 * @property membershipId Идентификатор invitation записи.
 * @property workspaceId Идентификатор рабочего пространства.
 * @property workspaceName Название рабочего пространства.
 * @property workspaceSlug Публичный slug рабочего пространства.
 * @property workspaceStatus Статус рабочего пространства.
 * @property permissionRole Роль, предложенная пользователю.
 * @property invitedByDisplayName Имя инициатора invitation.
 */
data class SessionWorkspaceInvitationSnapshot(
    val membershipId: String,
    val workspaceId: String,
    val workspaceName: String,
    val workspaceSlug: String,
    val workspaceStatus: String,
    val permissionRole: String,
    val invitedByDisplayName: String?,
)
