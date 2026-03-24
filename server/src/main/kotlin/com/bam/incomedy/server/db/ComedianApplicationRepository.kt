package com.bam.incomedy.server.db

import java.time.OffsetDateTime

/**
 * Lifecycle-статусы заявки комика на событие.
 *
 * @property wireName Стабильное wire/persistence значение статуса.
 */
enum class ComedianApplicationStatus(
    val wireName: String,
) {
    SUBMITTED("submitted"),
    SHORTLISTED("shortlisted"),
    APPROVED("approved"),
    WAITLISTED("waitlisted"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению из API/БД. */
        fun fromWireName(value: String): ComedianApplicationStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Сохраненная заявка комика на конкретное событие.
 *
 * @property id Идентификатор заявки.
 * @property eventId Идентификатор события, на которое подана заявка.
 * @property comedianUserId Идентификатор пользователя-комика.
 * @property comedianDisplayName Frozen/текущий display name комика для organizer list surface.
 * @property comedianUsername Необязательный username комика для organizer list surface.
 * @property status Текущий lifecycle-статус заявки.
 * @property note Необязательная текстовая заметка/комментарий от комика.
 * @property reviewedByUserId Необязательный reviewer, который последним менял review-статус.
 * @property reviewedByDisplayName Необязательное имя reviewer-а для organizer diagnostics/read model.
 * @property createdAt Момент создания заявки.
 * @property updatedAt Момент последнего изменения записи.
 * @property statusUpdatedAt Момент последнего изменения review-статуса.
 */
data class StoredComedianApplication(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String?,
    val status: ComedianApplicationStatus,
    val note: String? = null,
    val reviewedByUserId: String? = null,
    val reviewedByDisplayName: String? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val statusUpdatedAt: OffsetDateTime,
)

/**
 * Persistence-контракт backend slice-а `comedian applications`.
 */
interface ComedianApplicationRepository {
    /** Возвращает все заявки события для organizer review surface. */
    fun listEventApplications(eventId: String): List<StoredComedianApplication>

    /** Находит одну заявку по ее id внутри конкретного события. */
    fun findEventApplication(
        eventId: String,
        applicationId: String,
    ): StoredComedianApplication?

    /** Находит уже существующую заявку конкретного комика на событие. */
    fun findComedianApplication(
        eventId: String,
        comedianUserId: String,
    ): StoredComedianApplication?

    /** Создает новую заявку в статусе `submitted`. */
    fun createComedianApplication(
        eventId: String,
        comedianUserId: String,
        note: String?,
        status: ComedianApplicationStatus,
    ): StoredComedianApplication

    /** Обновляет review-статус существующей заявки. */
    fun updateComedianApplicationStatus(
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
        reviewedByUserId: String,
    ): StoredComedianApplication?
}
