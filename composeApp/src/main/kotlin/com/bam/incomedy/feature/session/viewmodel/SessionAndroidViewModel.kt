package com.bam.incomedy.feature.session.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import com.bam.incomedy.shared.session.SessionState
import com.bam.incomedy.shared.session.SessionViewModel
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

    /** Скрывает текущую ошибку главного экрана. */
    fun clearError() {
        sharedViewModel.clearError()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
