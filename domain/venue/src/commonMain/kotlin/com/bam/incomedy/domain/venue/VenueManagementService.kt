package com.bam.incomedy.domain.venue

/**
 * Контракт organizer venue management bounded context-а.
 *
 * Сервис отделяет клиентскую orchestration-логику от конкретного backend transport-а и задает
 * единый API для площадок и шаблонов схем зала.
 */
interface VenueManagementService {
    /** Возвращает список площадок и вложенных hall templates, доступных текущей сессии. */
    suspend fun listVenues(accessToken: String): Result<List<OrganizerVenue>>

    /** Создает новую площадку внутри выбранного organizer workspace. */
    suspend fun createVenue(
        accessToken: String,
        draft: VenueDraft,
    ): Result<OrganizerVenue>

    /** Создает новый hall template внутри площадки. */
    suspend fun createHallTemplate(
        accessToken: String,
        venueId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate>

    /** Обновляет существующий hall template и получает новую версию того же шаблона. */
    suspend fun updateHallTemplate(
        accessToken: String,
        templateId: String,
        draft: HallTemplateDraft,
    ): Result<HallTemplate>

    /** Клонирует hall template внутри той же площадки. */
    suspend fun cloneHallTemplate(
        accessToken: String,
        templateId: String,
        clonedName: String? = null,
    ): Result<HallTemplate>
}

/**
 * Площадка организатора с вложенными hall templates.
 *
 * @property id Идентификатор площадки.
 * @property workspaceId Workspace, которому принадлежит площадка.
 * @property name Название площадки.
 * @property city Город площадки.
 * @property address Почтовый адрес площадки.
 * @property timezone IANA timezone площадки.
 * @property capacity Базовая вместимость площадки.
 * @property description Краткое описание площадки и организационных особенностей.
 * @property contacts Контактные точки площадки или организатора.
 * @property hallTemplates Шаблоны схем зала, привязанные к площадке.
 */
data class OrganizerVenue(
    val id: String,
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contacts: List<VenueContact> = emptyList(),
    val hallTemplates: List<HallTemplate> = emptyList(),
)

/**
 * Черновик новой площадки, который клиент собирает до отправки на backend.
 *
 * @property workspaceId Workspace, внутри которого создается площадка.
 * @property name Название площадки.
 * @property city Город площадки.
 * @property address Адрес площадки.
 * @property timezone IANA timezone площадки.
 * @property capacity Вместимость площадки.
 * @property description Краткое описание площадки.
 * @property contacts Контакты площадки или организатора.
 */
data class VenueDraft(
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacity: Int,
    val description: String? = null,
    val contacts: List<VenueContact> = emptyList(),
)

/**
 * Контактная точка площадки.
 *
 * @property label Человеко-читаемое имя контакта.
 * @property value Канал связи: телефон, Telegram, email или другое безопасное значение.
 */
data class VenueContact(
    val label: String,
    val value: String,
)

/**
 * Сохраненный шаблон схемы зала.
 *
 * @property id Идентификатор шаблона.
 * @property venueId Идентификатор площадки-владельца.
 * @property name Название шаблона.
 * @property version Номер версии шаблона.
 * @property status Текущий lifecycle-status шаблона.
 * @property layout Каноническое typed-описание схемы зала.
 */
data class HallTemplate(
    val id: String,
    val venueId: String,
    val name: String,
    val version: Int,
    val status: HallTemplateStatus,
    val layout: HallLayout,
)

/**
 * Черновик создания или обновления hall template.
 *
 * @property name Название шаблона.
 * @property status Желаемый lifecycle-status шаблона.
 * @property layout Typed layout-представление builder v1.
 */
data class HallTemplateDraft(
    val name: String,
    val status: HallTemplateStatus = HallTemplateStatus.DRAFT,
    val layout: HallLayout,
)

/**
 * Статусы hall template внутри bounded venue context-а.
 *
 * @property wireName Значение, которое используется в HTTP API и persistence.
 */
enum class HallTemplateStatus(
    val wireName: String,
) {
    DRAFT("draft"),
    PUBLISHED("published"),
    ARCHIVED("archived"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению backend API. */
        fun fromWireName(value: String): HallTemplateStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Каноническая typed-схема hall template builder v1.
 *
 * @property stage Конфигурация сцены, если она задана.
 * @property priceZones Ценовые зоны шаблона.
 * @property zones Сектора и standing-зоны.
 * @property rows Ряды с отдельными местами.
 * @property tables Столы с группами мест.
 * @property serviceAreas Служебные или недоступные области.
 * @property blockedSeatRefs Список seat refs, которые нужно считать заблокированными.
 */
data class HallLayout(
    val stage: HallStage? = null,
    val priceZones: List<HallPriceZone> = emptyList(),
    val zones: List<HallZone> = emptyList(),
    val rows: List<HallRow> = emptyList(),
    val tables: List<HallTable> = emptyList(),
    val serviceAreas: List<HallServiceArea> = emptyList(),
    val blockedSeatRefs: List<String> = emptyList(),
)

/**
 * Сцена внутри схемы зала.
 *
 * @property label Отображаемое название сцены.
 * @property notes Необязательное пояснение по расположению или назначению.
 */
data class HallStage(
    val label: String,
    val notes: String? = null,
)

/**
 * Ценовая зона шаблона.
 *
 * @property id Стабильный идентификатор зоны внутри шаблона.
 * @property name Отображаемое название зоны.
 * @property defaultPriceMinor Базовая цена в minor units, если она уже известна.
 */
data class HallPriceZone(
    val id: String,
    val name: String,
    val defaultPriceMinor: Int? = null,
)

/**
 * Сектор или standing-зона внутри схемы.
 *
 * @property id Стабильный идентификатор зоны.
 * @property name Название сектора или standing-зоны.
 * @property capacity Вместимость зоны.
 * @property priceZoneId Связанная ценовая зона, если она назначена.
 * @property kind Тип зоны (`standing`, `sector`, `vip` и т.п.).
 */
data class HallZone(
    val id: String,
    val name: String,
    val capacity: Int,
    val priceZoneId: String? = null,
    val kind: String = "standing",
)

/**
 * Ряд с дискретными местами.
 *
 * @property id Стабильный идентификатор ряда.
 * @property label Отображаемая метка ряда.
 * @property seats Список мест внутри ряда.
 * @property priceZoneId Связанная ценовая зона ряда.
 */
data class HallRow(
    val id: String,
    val label: String,
    val seats: List<HallSeat>,
    val priceZoneId: String? = null,
)

/**
 * Отдельное место внутри ряда.
 *
 * @property ref Уникальная ссылка на место внутри шаблона.
 * @property label Отображаемый номер или код места.
 */
data class HallSeat(
    val ref: String,
    val label: String,
)

/**
 * Стол с группой мест.
 *
 * @property id Стабильный идентификатор стола.
 * @property label Название или номер стола.
 * @property seatCount Количество мест у стола.
 * @property priceZoneId Ценовая зона стола.
 */
data class HallTable(
    val id: String,
    val label: String,
    val seatCount: Int,
    val priceZoneId: String? = null,
)

/**
 * Служебная или техническая область схемы.
 *
 * @property id Стабильный идентификатор области.
 * @property name Отображаемое название области.
 * @property kind Тип области (`service`, `technical`, `barrier` и т.п.).
 */
data class HallServiceArea(
    val id: String,
    val name: String,
    val kind: String,
)
