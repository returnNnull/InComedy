package com.bam.incomedy.server.db

import java.time.OffsetDateTime

/**
 * Жизненный статус lineup entry.
 *
 * `draft` покрывает текущий foundation slice, а остальные значения зарезервированы под будущий
 * live-stage flow без повторного переименования wire-значений.
 *
 * @property wireName Стабильное wire/persistence значение статуса.
 */
enum class LineupEntryStatus(
    val wireName: String,
) {
    DRAFT("draft"),
    UP_NEXT("up_next"),
    ON_STAGE("on_stage"),
    DONE("done"),
    DELAYED("delayed"),
    DROPPED("dropped"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению из API/БД. */
        fun fromWireName(value: String): LineupEntryStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Сохраненная запись lineup конкретного события.
 *
 * @property id Идентификатор lineup entry.
 * @property eventId Идентификатор события.
 * @property comedianUserId Идентификатор комика.
 * @property comedianDisplayName Отображаемое имя комика для organizer read model.
 * @property comedianUsername Необязательный username комика.
 * @property applicationId Необязательная ссылка на исходную заявку.
 * @property orderIndex Явная позиция в lineup.
 * @property status Текущий статус lineup entry.
 * @property notes Необязательная organizer-заметка к слоту.
 * @property createdAt Момент создания записи.
 * @property updatedAt Момент последнего изменения записи.
 */
data class StoredLineupEntry(
    val id: String,
    val eventId: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val comedianUsername: String? = null,
    val applicationId: String? = null,
    val orderIndex: Int,
    val status: LineupEntryStatus,
    val notes: String? = null,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/**
 * DTO для batch-перестановки lineup entry.
 *
 * @property entryId Идентификатор переставляемой записи.
 * @property orderIndex Новая явная позиция.
 */
data class LineupEntryOrderUpdate(
    val entryId: String,
    val orderIndex: Int,
)

/**
 * Persistence-контракт backend slice-а `lineup`.
 */
interface LineupRepository {
    /** Возвращает полный lineup события в стабильном порядке. */
    fun listEventLineup(eventId: String): List<StoredLineupEntry>

    /** Находит lineup entry, если он уже связан с конкретной заявкой. */
    fun findApplicationLineupEntry(
        eventId: String,
        applicationId: String,
    ): StoredLineupEntry?

    /** Создает новую draft-запись lineup с автоматическим следующим `order_index`. */
    fun createLineupEntry(
        eventId: String,
        comedianUserId: String,
        applicationId: String?,
        status: LineupEntryStatus,
        notes: String?,
    ): StoredLineupEntry

    /** Обновляет live-stage статус одной записи lineup и возвращает актуальный lineup события. */
    fun updateLineupEntryStatus(
        eventId: String,
        entryId: String,
        status: LineupEntryStatus,
    ): List<StoredLineupEntry>

    /** Перезаписывает порядок всего lineup события атомарно. */
    fun reorderEventLineup(
        eventId: String,
        updates: List<LineupEntryOrderUpdate>,
    ): List<StoredLineupEntry>
}
