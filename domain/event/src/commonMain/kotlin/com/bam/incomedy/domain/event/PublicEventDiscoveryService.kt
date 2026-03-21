package com.bam.incomedy.domain.event

/**
 * Контракт публичного audience discovery surface для опубликованных мероприятий.
 *
 * Сервис скрывает transport-детали backend route-а и дает будущим audience-клиентам единый API
 * для каталога `city/date/price`.
 */
interface PublicEventDiscoveryService {
    /** Возвращает audience-safe список опубликованных public-событий по детерминированным фильтрам. */
    suspend fun listPublicEvents(
        filter: PublicEventDiscoveryFilter = PublicEventDiscoveryFilter(),
    ): Result<List<PublicEventSummary>>
}

/**
 * Набор фильтров публичного discovery route-а.
 *
 * @property city Необязательный фильтр по городу площадки.
 * @property dateFromIso Необязательная нижняя граница локальной даты события в формате `YYYY-MM-DD`.
 * @property dateToIso Необязательная верхняя граница локальной даты события в формате `YYYY-MM-DD`.
 * @property priceMinMinor Необязательная нижняя граница диапазона цены в minor units.
 * @property priceMaxMinor Необязательная верхняя граница диапазона цены в minor units.
 */
data class PublicEventDiscoveryFilter(
    val city: String? = null,
    val dateFromIso: String? = null,
    val dateToIso: String? = null,
    val priceMinMinor: Int? = null,
    val priceMaxMinor: Int? = null,
)

/**
 * Audience-safe карточка мероприятия для публичного каталога.
 *
 * @property id Идентификатор события.
 * @property title Название мероприятия.
 * @property description Необязательное краткое описание.
 * @property venueName Человекочитаемое имя площадки.
 * @property city Город площадки.
 * @property startsAtIso RFC3339 timestamp начала мероприятия.
 * @property doorsOpenAtIso Необязательное время открытия дверей.
 * @property endsAtIso Необязательное время завершения.
 * @property salesStatus Текущий статус продаж для audience surface.
 * @property currency Базовая валюта ценового диапазона.
 * @property priceMinMinor Минимальная цена среди event-local price zones.
 * @property priceMaxMinor Максимальная цена среди event-local price zones.
 */
data class PublicEventSummary(
    val id: String,
    val title: String,
    val description: String? = null,
    val venueName: String,
    val city: String,
    val startsAtIso: String,
    val doorsOpenAtIso: String? = null,
    val endsAtIso: String? = null,
    val salesStatus: EventSalesStatus,
    val currency: String,
    val priceMinMinor: Int? = null,
    val priceMaxMinor: Int? = null,
)
