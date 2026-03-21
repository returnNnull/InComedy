package com.bam.incomedy.server.events

import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.StoredOrganizerEvent
import com.bam.incomedy.server.db.StoredVenue
import com.bam.incomedy.server.db.VenueRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Сервис публичного discovery-каталога для audience surface.
 *
 * Сервис берет только опубликованные public-события, подтягивает безопасный venue-контекст,
 * считает ценовой диапазон и применяет детерминированные фильтры `city/date/price` до ответа route-а.
 *
 * @property eventRepository Репозиторий organizer events, уже умеющий отделять public-события.
 * @property venueRepository Репозиторий площадок, из которого берутся город и timezone события.
 */
class PublicEventDiscoveryService(
    private val eventRepository: EventRepository,
    private val venueRepository: VenueRepository,
) {
    /** Возвращает audience-safe summaries опубликованных public-событий по валидированным фильтрам. */
    fun listPublicEvents(
        query: PublicEventDiscoveryQuery,
    ): List<PublicEventSummaryView> {
        return eventRepository.listPublicEvents()
            .map { event ->
                val venue = venueRepository.findVenue(event.venueId)
                    ?: throw IllegalStateException("Venue ${event.venueId} was not found for public event ${event.id}")
                event.toSummary(venue = venue)
            }
            .filter { summary -> summary.matches(query) }
            .sortedWith(compareBy<PublicEventSummaryView> { it.startsAt }.thenBy { it.id })
    }

    /** Преобразует organizer event и venue metadata в audience-safe карточку каталога. */
    private fun StoredOrganizerEvent.toSummary(
        venue: StoredVenue,
    ): PublicEventSummaryView {
        val priceMinMinor = priceZones.minOfOrNull { zone -> zone.priceMinor }
        val priceMaxMinor = priceZones.maxOfOrNull { zone -> zone.priceMinor }
        val venueZoneId = venue.resolveZoneId(fallbackZoneId = startsAt.offset)
        return PublicEventSummaryView(
            id = id,
            title = title,
            description = description,
            venueName = venueName,
            city = venue.city,
            startsAt = startsAt,
            doorsOpenAt = doorsOpenAt,
            endsAt = endsAt,
            localDate = startsAt.atZoneSameInstant(venueZoneId).toLocalDate(),
            salesStatus = salesStatus,
            currency = currency,
            priceMinMinor = priceMinMinor,
            priceMaxMinor = priceMaxMinor,
        )
    }

    /** Проверяет попадание карточки в `city/date/price` фильтры audience discovery. */
    private fun PublicEventSummaryView.matches(
        query: PublicEventDiscoveryQuery,
    ): Boolean {
        if (query.city != null && !city.equals(query.city, ignoreCase = true)) {
            return false
        }
        if (query.dateFrom != null && localDate < query.dateFrom) {
            return false
        }
        if (query.dateTo != null && localDate > query.dateTo) {
            return false
        }
        if (query.priceMinMinor != null) {
            val currentPriceMax = priceMaxMinor ?: return false
            if (currentPriceMax < query.priceMinMinor) {
                return false
            }
        }
        if (query.priceMaxMinor != null) {
            val currentPriceMin = priceMinMinor ?: return false
            if (currentPriceMin > query.priceMaxMinor) {
                return false
            }
        }
        return true
    }

    /** Возвращает timezone площадки или безопасно падает обратно на offset времени события. */
    private fun StoredVenue.resolveZoneId(
        fallbackZoneId: ZoneId,
    ): ZoneId {
        return runCatching { ZoneId.of(timezone) }.getOrDefault(fallbackZoneId)
    }
}

/**
 * Валидированная query-модель публичного discovery route-а.
 *
 * @property city Нормализованный фильтр по городу или `null`, если фильтр не задан.
 * @property dateFrom Нижняя граница локальной даты события.
 * @property dateTo Верхняя граница локальной даты события.
 * @property priceMinMinor Нижняя граница искомого ценового диапазона.
 * @property priceMaxMinor Верхняя граница искомого ценового диапазона.
 */
data class PublicEventDiscoveryQuery(
    val city: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val priceMinMinor: Int? = null,
    val priceMaxMinor: Int? = null,
)

/**
 * Внутреннее audience-safe представление карточки публичного события.
 *
 * @property id Идентификатор события.
 * @property title Заголовок карточки.
 * @property description Необязательное описание.
 * @property venueName Имя площадки.
 * @property city Город площадки.
 * @property startsAt Время начала в исходном offset-формате события.
 * @property doorsOpenAt Необязательное время открытия дверей.
 * @property endsAt Необязательное время завершения.
 * @property localDate Локальная дата события в timezone площадки для date-фильтров.
 * @property salesStatus Текущий статус продаж.
 * @property currency Валюта карточки.
 * @property priceMinMinor Минимальная цена среди event-local zones.
 * @property priceMaxMinor Максимальная цена среди event-local zones.
 */
data class PublicEventSummaryView(
    val id: String,
    val title: String,
    val description: String? = null,
    val venueName: String,
    val city: String,
    val startsAt: OffsetDateTime,
    val doorsOpenAt: OffsetDateTime? = null,
    val endsAt: OffsetDateTime? = null,
    val localDate: LocalDate,
    val salesStatus: String,
    val currency: String,
    val priceMinMinor: Int? = null,
    val priceMaxMinor: Int? = null,
)
