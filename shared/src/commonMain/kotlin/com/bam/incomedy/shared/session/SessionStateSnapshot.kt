package com.bam.incomedy.shared.session

/**
 * Снимок состояния сессии для bridge-слоя, который безопасно экспортируется в iOS.
 *
 * @property isAuthorized Показывает, что пользователь авторизован.
 * @property providerKey Ключ основного провайдера авторизации.
 * @property accessToken Текущий access token сессии.
 * @property refreshToken Текущий refresh token сессии.
 * @property userId Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username профиля, если он известен.
 * @property photoUrl Ссылка на фотографию профиля.
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Все привязанные способы входа.
 * @property workspaces Экспортируемые рабочие пространства для iOS-слоя.
 * @property isLoadingContext Показывает, что загружается контекст сессии.
 * @property isUpdatingRole Показывает, что сейчас выполняется смена роли.
 * @property isCreatingWorkspace Показывает, что сейчас создается рабочее пространство.
 * @property errorMessage Последняя ошибка слоя сессии.
 */
data class SessionStateSnapshot(
    val isAuthorized: Boolean,
    val providerKey: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?,
    val displayName: String?,
    val username: String?,
    val photoUrl: String?,
    val roles: List<String>,
    val activeRole: String?,
    val linkedProviders: List<String>,
    val workspaces: List<SessionWorkspaceSnapshot>,
    val isLoadingContext: Boolean,
    val isUpdatingRole: Boolean,
    val isCreatingWorkspace: Boolean,
    val errorMessage: String?,
)
