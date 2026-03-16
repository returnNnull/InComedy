import Foundation
import Shared

/// SwiftUI-модель organizer venue management feature.
///
/// Модель адаптирует общий `VenueBridge` к локальным published-полям и не дает SwiftUI зависеть
/// от Kotlin snapshot-типов напрямую.
final class VenueScreenModel: ObservableObject {
    /// Площадки и вложенные hall templates текущей organizer-сессии.
    @Published var venues: [VenueItem] = []

    /// Показывает загрузку списка площадок.
    @Published var isLoading: Bool = false

    /// Показывает активную мутацию create/update/clone.
    @Published var isSubmitting: Bool = false

    /// Хранит верхнеуровневую безопасную ошибку venue feature.
    @Published var errorMessage: String?

    /// Bridge к общей venue feature model.
    private let bridge: VenueBridge?

    /// Удерживает текущую подписку на Kotlin state updates.
    private var bindingHandle: NSObject?

    /// Создает живую SwiftUI-модель и сразу синхронизирует состояние из общего KMP-слоя.
    ///
    /// - Parameter bridge: Необязательный bridge для тестов.
    init(bridge: VenueBridge? = nil) {
        self.bridge = bridge ?? VenueBridge(
            viewModel: InComedyKoin.shared.getVenueViewModel()
        )
        bind()
        self.bridge?.loadVenues()
    }

    /// Создает fixture-версию модели без подключения к реальному bridge.
    ///
    /// - Parameter fixture: Предзагруженное состояние для превью и UI-тестов.
    init(fixture: VenueScreenFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    /// Выполняет ручной refresh organizer venue списка.
    func refresh() {
        if let bridge {
            bridge.loadVenues()
        }
    }

    /// Создает площадку из текстовой SwiftUI-формы.
    ///
    /// - Parameters:
    ///   - workspaceId: Идентификатор выбранного organizer workspace.
    ///   - name: Название площадки.
    ///   - city: Город площадки.
    ///   - address: Адрес площадки.
    ///   - timezone: IANA timezone площадки.
    ///   - capacityText: Вместимость в текстовом виде.
    ///   - description: Необязательное описание площадки.
    ///   - contactsText: Контакты формата `label|value`.
    func createVenue(
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacityText: String,
        description: String,
        contactsText: String
    ) {
        guard let bridge else { return }
        guard let capacity = Int(capacityText.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            errorMessage = "Вместимость площадки должна быть целым числом"
            return
        }
        bridge.createVenue(
            workspaceId: workspaceId,
            name: name,
            city: city,
            address: address,
            timezone: timezone,
            capacity: Int32(capacity),
            description: description,
            contactsText: contactsText
        )
    }

    /// Создает или обновляет hall template из SwiftUI builder form.
    ///
    /// - Parameters:
    ///   - venueId: Идентификатор площадки-владельца.
    ///   - templateId: Необязательный id редактируемого шаблона.
    ///   - name: Название шаблона.
    ///   - statusKey: Wire-ключ статуса шаблона.
    ///   - stageLabel: Название сцены.
    ///   - priceZonesText: Текст ценовых зон.
    ///   - zonesText: Текст standing/sector зон.
    ///   - rowsText: Текст рядов.
    ///   - tablesText: Текст столов.
    ///   - serviceAreasText: Текст служебных зон.
    ///   - blockedSeatRefsText: Список blocked seat refs.
    func saveHallTemplate(
        venueId: String,
        templateId: String?,
        name: String,
        statusKey: String,
        stageLabel: String,
        priceZonesText: String,
        zonesText: String,
        rowsText: String,
        tablesText: String,
        serviceAreasText: String,
        blockedSeatRefsText: String
    ) {
        bridge?.saveHallTemplate(
            venueId: venueId,
            templateId: templateId,
            name: name,
            statusKey: statusKey,
            stageLabel: stageLabel,
            priceZonesText: priceZonesText,
            zonesText: zonesText,
            rowsText: rowsText,
            tablesText: tablesText,
            serviceAreasText: serviceAreasText,
            blockedSeatRefsText: blockedSeatRefsText
        )
    }

    /// Клонирует существующий hall template.
    ///
    /// - Parameters:
    ///   - templateId: Идентификатор исходного шаблона.
    ///   - clonedName: Необязательное новое имя.
    func cloneHallTemplate(templateId: String, clonedName: String?) {
        bridge?.cloneHallTemplate(
            templateId: templateId,
            clonedName: clonedName
        )
    }

    /// Очищает текущую venue error.
    func clearError() {
        if let bridge {
            bridge.clearError()
        } else {
            errorMessage = nil
        }
    }

    /// Подписывается на Kotlin state flow и применяет snapshots к SwiftUI-полям.
    private func bind() {
        guard let bridge else { return }
        setBinding(
            bridge.observeState { [weak self] snapshot in
                guard let self else { return }
                Task { @MainActor in
                    self.apply(snapshot: snapshot)
                }
            }
        )
    }

    /// Применяет bridge snapshot к локальным published-полям.
    ///
    /// - Parameter snapshot: Снимок organizer venue feature из KMP-слоя.
    @MainActor
    private func apply(snapshot: VenueStateSnapshot) {
        venues = snapshot.venues.map(VenueItem.init(snapshot:))
        isLoading = snapshot.isLoading
        isSubmitting = snapshot.isSubmitting
        errorMessage = snapshot.errorMessage
    }

    /// Применяет fixture-состояние к published-полям без реального backend bridge.
    ///
    /// - Parameter fixture: Локальная fixture venue feature.
    private func apply(fixture: VenueScreenFixture) {
        venues = fixture.venues
        isLoading = fixture.isLoading
        isSubmitting = fixture.isSubmitting
        errorMessage = fixture.errorMessage
    }

    /// Переназначает активную bridge-подписку.
    ///
    /// - Parameter handle: Новый handle подписки.
    private func setBinding(_ handle: Any) {
        disposeBindingIfNeeded()
        bindingHandle = handle as? NSObject
    }

    /// Освобождает текущую bridge-подписку, если она еще активна.
    private func disposeBindingIfNeeded() {
        guard let bindingHandle else { return }
        let disposeSelector = NSSelectorFromString("dispose")
        if bindingHandle.responds(to: disposeSelector) {
            _ = bindingHandle.perform(disposeSelector)
        }
        self.bindingHandle = nil
    }
}

/// Fixture organizer venue feature для превью и UI-тестов.
struct VenueScreenFixture {
    /// Площадки и шаблоны, которые нужно показать во fixture-режиме.
    let venues: [VenueItem]

    /// Признак активной загрузки данных.
    let isLoading: Bool

    /// Признак активной мутации.
    let isSubmitting: Bool

    /// Текст безопасной ошибки.
    let errorMessage: String?

    /// Возвращает типовую organizer fixture с одной площадкой и одним шаблоном зала.
    ///
    /// - Parameter workspaceId: Workspace, к которому привязана fixture-площадка.
    static func preview(workspaceId: String = "ws-1") -> VenueScreenFixture {
        return VenueScreenFixture(
            venues: [
                VenueItem(
                    id: "venue-1",
                    workspaceId: workspaceId,
                    name: "Moscow Cellar",
                    city: "Moscow",
                    address: "Tverskaya 1",
                    timezone: "Europe/Moscow",
                    capacity: 120,
                    description: "Клубный зал для вечерних шоу",
                    contactsText: "Telegram|@cellar",
                    hallTemplates: [
                        VenueTemplateItem(
                            id: "template-1",
                            venueId: "venue-1",
                            name: "Late Layout",
                            version: 2,
                            statusKey: "published",
                            summaryText: "ценовых зон: 1 · рядов: 1 · blocked seats: 1",
                            stageLabel: "",
                            priceZonesText: "vip|VIP|3000",
                            zonesText: "",
                            rowsText: "row-a|A|3|vip",
                            tablesText: "",
                            serviceAreasText: "",
                            blockedSeatRefsText: "row-a-3"
                        )
                    ]
                )
            ],
            isLoading: false,
            isSubmitting: false,
            errorMessage: nil
        )
    }
}

/// Площадка organizer-а для SwiftUI-слоя.
struct VenueItem: Identifiable {
    /// Уникальный идентификатор площадки.
    let id: String

    /// Workspace-владелец площадки.
    let workspaceId: String

    /// Название площадки.
    let name: String

    /// Город площадки.
    let city: String

    /// Адрес площадки.
    let address: String

    /// IANA timezone площадки.
    let timezone: String

    /// Базовая вместимость площадки.
    let capacity: Int

    /// Необязательное описание площадки.
    let description: String?

    /// Контакты площадки в multiline-формате `label|value`.
    let contactsText: String

    /// Шаблоны схем зала площадки.
    let hallTemplates: [VenueTemplateItem]

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot площадки из общего bridge.
    init(snapshot: VenueSnapshot) {
        id = snapshot.id
        workspaceId = snapshot.workspaceId
        name = snapshot.name
        city = snapshot.city
        address = snapshot.address
        timezone = snapshot.timezone
        capacity = Int(snapshot.capacity)
        description = snapshot.description_
        contactsText = snapshot.contactsText
        hallTemplates = snapshot.hallTemplates.map(VenueTemplateItem.init(snapshot:))
    }

    /// Явный инициализатор для fixture-сценариев.
    init(
        id: String,
        workspaceId: String,
        name: String,
        city: String,
        address: String,
        timezone: String,
        capacity: Int,
        description: String?,
        contactsText: String,
        hallTemplates: [VenueTemplateItem]
    ) {
        self.id = id
        self.workspaceId = workspaceId
        self.name = name
        self.city = city
        self.address = address
        self.timezone = timezone
        self.capacity = capacity
        self.description = description
        self.contactsText = contactsText
        self.hallTemplates = hallTemplates
    }
}

/// Hall template для SwiftUI builder surface.
struct VenueTemplateItem: Identifiable {
    /// Уникальный идентификатор шаблона.
    let id: String

    /// Идентификатор площадки-владельца.
    let venueId: String

    /// Название шаблона.
    let name: String

    /// Текущая версия шаблона.
    let version: Int

    /// Wire-ключ lifecycle-статуса.
    let statusKey: String

    /// Краткая summary layout-а.
    let summaryText: String

    /// Поле сцены builder-формы.
    let stageLabel: String

    /// Поле ценовых зон builder-формы.
    let priceZonesText: String

    /// Поле standing/sector зон builder-формы.
    let zonesText: String

    /// Поле рядов builder-формы.
    let rowsText: String

    /// Поле столов builder-формы.
    let tablesText: String

    /// Поле служебных зон builder-формы.
    let serviceAreasText: String

    /// Поле blocked seat refs builder-формы.
    let blockedSeatRefsText: String

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель шаблона.
    ///
    /// - Parameter snapshot: Snapshot шаблона из общего bridge.
    init(snapshot: HallTemplateSnapshot) {
        id = snapshot.id
        venueId = snapshot.venueId
        name = snapshot.name
        version = Int(snapshot.version)
        statusKey = snapshot.statusKey
        summaryText = snapshot.summaryText
        stageLabel = snapshot.stageLabel
        priceZonesText = snapshot.priceZonesText
        zonesText = snapshot.zonesText
        rowsText = snapshot.rowsText
        tablesText = snapshot.tablesText
        serviceAreasText = snapshot.serviceAreasText
        blockedSeatRefsText = snapshot.blockedSeatRefsText
    }

    /// Явный инициализатор для fixture-сценариев.
    init(
        id: String,
        venueId: String,
        name: String,
        version: Int,
        statusKey: String,
        summaryText: String,
        stageLabel: String,
        priceZonesText: String,
        zonesText: String,
        rowsText: String,
        tablesText: String,
        serviceAreasText: String,
        blockedSeatRefsText: String
    ) {
        self.id = id
        self.venueId = venueId
        self.name = name
        self.version = version
        self.statusKey = statusKey
        self.summaryText = summaryText
        self.stageLabel = stageLabel
        self.priceZonesText = priceZonesText
        self.zonesText = zonesText
        self.rowsText = rowsText
        self.tablesText = tablesText
        self.serviceAreasText = serviceAreasText
        self.blockedSeatRefsText = blockedSeatRefsText
    }
}
