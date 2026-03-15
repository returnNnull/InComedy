package com.bam.incomedy.feature.auth.domain

/**
 * Запрос на запуск внешнего auth-провайдера.
 *
 * @property provider Провайдер, через которого запускается вход.
 * @property state Сгенерированный state для защиты auth-потока.
 * @property url Ссылка для открытия внешнего auth-потока, если провайдер использует browser-based launch.
 */
data class AuthLaunchRequest(
    val provider: AuthProviderType,
    val state: String,
    val url: String = "",
)

/**
 * Авторизованная сессия пользователя после завершения входа.
 *
 * @property provider Провайдер, через которого был выполнен вход.
 * @property userId Внутренний идентификатор пользователя.
 * @property accessToken Access token текущей сессии.
 * @property refreshToken Refresh token текущей сессии, если он выдан.
 * @property user Профиль авторизованного пользователя.
 */
data class AuthSession(
    val provider: AuthProviderType,
    val userId: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val user: AuthorizedUser,
)

/**
 * Профиль пользователя, доступный после авторизации или восстановления сессии.
 *
 * @property id Внутренний идентификатор пользователя.
 * @property displayName Отображаемое имя профиля.
 * @property username Username пользователя, если он есть.
 * @property photoUrl URL фотографии профиля.
 * @property roles Список доступных ролей пользователя.
 * @property activeRole Текущая активная роль пользователя.
 * @property linkedProviders Список привязанных auth-провайдеров.
 */
data class AuthorizedUser(
    val id: String,
    val displayName: String,
    val username: String? = null,
    val photoUrl: String? = null,
    val roles: List<String> = emptyList(),
    val activeRole: String? = null,
    val linkedProviders: List<String> = emptyList(),
)
