package com.bam.incomedy.feature.session.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import com.bam.incomedy.shared.session.SessionState
import com.bam.incomedy.shared.session.SessionViewModel
import com.bam.incomedy.domain.session.WorkspaceInvitationDecision
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер общей модели сессии для экранов авторизованной части приложения.
 *
 * @property application Android application context.
 */
class SessionAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    /** Общая модель сессии из KMP-слоя. */
    private val sharedViewModel: SessionViewModel = InComedyKoin.getSessionViewModel()

    /** Состояние общей сессии для Android UI. */
    val state: StateFlow<SessionState> = sharedViewModel.state

    /** Выполняет выход пользователя. */
    fun signOut() {
        sharedViewModel.signOut()
    }

    /** Переключает активную роль текущего пользователя. */
    fun setActiveRole(role: String) {
        sharedViewModel.setActiveRole(role)
    }

    /** Создает новое рабочее пространство организатора. */
    fun createWorkspace(name: String, slug: String? = null) {
        sharedViewModel.createWorkspace(name = name, slug = slug)
    }

    /** Создает invitation существующему пользователю внутри workspace. */
    fun createWorkspaceInvitation(
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String,
    ) {
        sharedViewModel.createWorkspaceInvitation(
            workspaceId = workspaceId,
            inviteeIdentifier = inviteeIdentifier,
            permissionRole = permissionRole,
        )
    }

    /** Принимает pending invitation текущего пользователя. */
    fun acceptWorkspaceInvitation(membershipId: String) {
        sharedViewModel.respondToWorkspaceInvitation(
            membershipId = membershipId,
            decision = WorkspaceInvitationDecision.ACCEPT,
        )
    }

    /** Отклоняет pending invitation текущего пользователя. */
    fun declineWorkspaceInvitation(membershipId: String) {
        sharedViewModel.respondToWorkspaceInvitation(
            membershipId = membershipId,
            decision = WorkspaceInvitationDecision.DECLINE,
        )
    }

    /** Меняет permission role membership внутри workspace. */
    fun updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: String,
    ) {
        sharedViewModel.updateWorkspaceMembershipRole(
            workspaceId = workspaceId,
            membershipId = membershipId,
            permissionRole = permissionRole,
        )
    }

    /** Скрывает текущую ошибку главного экрана. */
    fun clearError() {
        sharedViewModel.clearError()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
