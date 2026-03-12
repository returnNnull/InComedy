package com.bam.incomedy.feature.auth.domain

/**
 * Контракт доступа к расширенному контексту авторизованной сессии:
 * ролям, активной роли и рабочим пространствам.
 */
interface SessionContextService {
    /** Возвращает роли пользователя, активную роль и привязанные провайдеры. */
    suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext>

    /** Переключает активную роль пользователя. */
    suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext>

    /** Возвращает рабочие пространства текущего пользователя. */
    suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>>

    /** Создает рабочее пространство текущего пользователя. */
    suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String? = null,
    ): Result<OrganizerWorkspace>
}

/**
 * Контекст ролей текущей сессии.
 *
 * @property roles Все доступные роли пользователя.
 * @property activeRole Текущая активная роль.
 * @property linkedProviders Привязанные auth-провайдеры.
 */
data class SessionRoleContext(
    val roles: List<String>,
    val activeRole: String? = null,
    val linkedProviders: List<String> = emptyList(),
)

/**
 * Рабочее пространство организатора, доступное в текущей сессии.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль пользователя в этом рабочем пространстве.
 */
data class OrganizerWorkspace(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val permissionRole: String,
)
