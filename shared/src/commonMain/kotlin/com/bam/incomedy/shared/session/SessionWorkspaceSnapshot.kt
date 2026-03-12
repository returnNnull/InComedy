package com.bam.incomedy.shared.session

/**
 * Bridge-представление рабочего пространства для iOS-слоя.
 *
 * @property id Уникальный идентификатор рабочего пространства.
 * @property name Название рабочего пространства.
 * @property slug Публичный slug рабочего пространства.
 * @property status Текущий статус рабочего пространства.
 * @property permissionRole Роль пользователя внутри рабочего пространства.
 */
data class SessionWorkspaceSnapshot(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val permissionRole: String,
)
