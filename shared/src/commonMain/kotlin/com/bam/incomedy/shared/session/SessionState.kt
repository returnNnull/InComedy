package com.bam.incomedy.shared.session

import com.bam.incomedy.feature.auth.domain.AuthProviderType
import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace

/**
 * Полное состояние авторизованной сессии, которое используют Android и общий UI-слой.
 *
 * @property isAuthorized Показывает, что пользователь авторизован.
 * @property provider Текущий базовый провайдер авторизации.
 * @property accessToken Текущий access token сессии.
 * @property refreshToken Текущий refresh token сессии.
 * @property userId Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля, если он известен.
 * @property photoUrl Ссылка на фотографию профиля.
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Все привязанные способы входа.
 * @property workspaces Рабочие пространства организатора, доступные пользователю.
 * @property isLoadingContext Показывает, что загружается расширенный контекст сессии.
 * @property isUpdatingRole Показывает, что сейчас выполняется смена роли.
 * @property isCreatingWorkspace Показывает, что сейчас создается рабочее пространство.
 * @property errorMessage Последняя ошибка контекста сессии.
 */
data class SessionState(
    val isAuthorized: Boolean = false,
    val provider: AuthProviderType? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    val activeRole: String? = null,
    val linkedProviders: List<String> = emptyList(),
    val workspaces: List<OrganizerWorkspace> = emptyList(),
    val isLoadingContext: Boolean = false,
    val isUpdatingRole: Boolean = false,
    val isCreatingWorkspace: Boolean = false,
    val errorMessage: String? = null,
)
