package com.bam.incomedy.domain.lineup

/**
 * Контракт comedian applications и organizer lineup management slice-а.
 *
 * Сервис объединяет comedian submit flow и organizer-side review/reorder операции над уже
 * существующим backend foundation, чтобы shared/platform слои могли опираться на один bounded
 * context до появления platform-specific UI.
 */
interface LineupManagementService {
    /** Отправляет новую comedian application на опубликованное событие. */
    suspend fun submitApplication(
        accessToken: String,
        eventId: String,
        note: String?,
    ): Result<ComedianApplication>

    /** Загружает organizer/manager список заявок события. */
    suspend fun listEventApplications(
        accessToken: String,
        eventId: String,
    ): Result<List<ComedianApplication>>

    /** Меняет review-статус конкретной заявки события. */
    suspend fun updateApplicationStatus(
        accessToken: String,
        eventId: String,
        applicationId: String,
        status: ComedianApplicationStatus,
    ): Result<ComedianApplication>

    /** Загружает organizer/host lineup события в явном порядке. */
    suspend fun listEventLineup(
        accessToken: String,
        eventId: String,
    ): Result<List<LineupEntry>>

    /** Переставляет полный lineup события через explicit `order_index`. */
    suspend fun reorderLineup(
        accessToken: String,
        eventId: String,
        entries: List<LineupEntryOrderUpdate>,
    ): Result<List<LineupEntry>>
}

/**
 * Доменное представление заявки комика на событие.
 *
 * @property id Идентификатор заявки.
 * @property eventId Идентификатор события.
 * @property comedianUserId Идентификатор пользователя-комика.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property comedianUsername Необязательный username комика.
 * @property status Текущий review-статус заявки.
 * @property note Необязательная заметка комика.
 * @property reviewedByUserId Идентификатор reviewer-а, если review уже был.
 * @property reviewedByDisplayName Отображаемое имя reviewer-а.
 * @property createdAtIso ISO-время создания заявки.
 * @property updatedAtIso ISO-время последнего обновления.
 * @property statusUpdatedAtIso ISO-время последней смены статуса.
 */
data class ComedianApplication(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String? = null,
    val status: ComedianApplicationStatus,
    val note: String? = null,
    val reviewedByUserId: String? = null,
    val reviewedByDisplayName: String? = null,
    val createdAtIso: String,
    val updatedAtIso: String,
    val statusUpdatedAtIso: String,
)

/**
 * Поддерживаемые статусы comedian application.
 *
 * @property wireName Значение статуса в backend API.
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
        /** Восстанавливает enum по wire-значению backend API. */
        fun fromWireName(value: String): ComedianApplicationStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Доменное представление одного organizer lineup entry.
 *
 * @property id Идентификатор записи lineup.
 * @property eventId Идентификатор события.
 * @property comedianUserId Идентификатор комика.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property comedianUsername Необязательный username комика.
 * @property applicationId Ссылка на исходную approved-заявку, если она есть.
 * @property orderIndex Явная позиция внутри lineup.
 * @property status Текущий статус записи lineup.
 * @property notes Необязательная organizer-заметка.
 * @property createdAtIso ISO-время создания записи.
 * @property updatedAtIso ISO-время последнего обновления.
 */
data class LineupEntry(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String? = null,
    val applicationId: String? = null,
    val orderIndex: Int,
    val status: LineupEntryStatus,
    val notes: String? = null,
    val createdAtIso: String,
    val updatedAtIso: String,
)

/**
 * Поддерживаемые статусы lineup entry в текущем MVP slice-е.
 *
 * @property wireName Значение статуса в backend API.
 */
enum class LineupEntryStatus(
    val wireName: String,
) {
    DRAFT("draft"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению backend API. */
        fun fromWireName(value: String): LineupEntryStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Команда перестановки одного lineup entry.
 *
 * @property entryId Идентификатор записи, которую нужно переставить.
 * @property orderIndex Новая явная позиция внутри lineup.
 */
data class LineupEntryOrderUpdate(
    val entryId: String,
    val orderIndex: Int,
)
