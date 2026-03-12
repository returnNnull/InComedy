package com.bam.incomedy.data.auth.backend

import com.bam.incomedy.feature.auth.domain.OrganizerWorkspace
import com.bam.incomedy.feature.auth.domain.SessionContextService
import com.bam.incomedy.feature.auth.domain.SessionRoleContext

/**
 * Реализация `SessionContextService`, которая проксирует запросы контекста сессии
 * в текущий backend API.
 *
 * @property telegramBackendApi HTTP-клиент backend API авторизации и профиля.
 */
class BackendSessionContextService(
    private val telegramBackendApi: TelegramBackendApi,
) : SessionContextService {

    /** Загружает роли, активную роль и привязанные провайдеры пользователя. */
    override suspend fun getRoleContext(accessToken: String): Result<SessionRoleContext> {
        return telegramBackendApi.getRoleContext(accessToken)
    }

    /** Переключает активную роль пользователя. */
    override suspend fun setActiveRole(accessToken: String, role: String): Result<SessionRoleContext> {
        return telegramBackendApi.setActiveRole(
            accessToken = accessToken,
            role = role,
        )
    }

    /** Загружает рабочие пространства текущего пользователя. */
    override suspend fun listWorkspaces(accessToken: String): Result<List<OrganizerWorkspace>> {
        return telegramBackendApi.listWorkspaces(accessToken)
    }

    /** Создает новое рабочее пространство для текущего пользователя. */
    override suspend fun createWorkspace(
        accessToken: String,
        name: String,
        slug: String?,
    ): Result<OrganizerWorkspace> {
        return telegramBackendApi.createWorkspace(
            accessToken = accessToken,
            name = name,
            slug = slug,
        )
    }
}
