import Foundation
import Shared

/// SwiftUI-модель organizer event management feature.
///
/// Модель адаптирует общий `EventBridge` к локальным published-полям и не дает SwiftUI зависеть
/// от Kotlin snapshot-типов напрямую.
final class EventScreenModel: ObservableObject {
    /// События текущей organizer-сессии.
    @Published var events: [EventItem] = []

    /// Площадки и шаблоны, доступные для формы события.
    @Published var venues: [EventVenueItem] = []

    /// Показывает загрузку event context.
    @Published var isLoading: Bool = false

    /// Показывает активную organizer event мутацию.
    @Published var isSubmitting: Bool = false

    /// Хранит верхнеуровневую безопасную ошибку event feature.
    @Published var errorMessage: String?

    /// Bridge к общей event feature model.
    private let bridge: EventBridge?

    /// Удерживает текущую подписку на Kotlin state updates.
    private var bindingHandle: NSObject?

    /// Создает живую SwiftUI-модель и сразу синхронизирует состояние из общего KMP-слоя.
    ///
    /// - Parameter bridge: Необязательный bridge для тестов.
    init(bridge: EventBridge? = nil) {
        self.bridge = bridge ?? EventBridge(
            viewModel: InComedyKoin.shared.getEventViewModel()
        )
        bind()
        self.bridge?.loadContext()
    }

    /// Создает fixture-версию модели без подключения к реальному bridge.
    ///
    /// - Parameter fixture: Предзагруженное состояние для превью и UI-тестов.
    init(fixture: EventScreenFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    /// Выполняет ручной refresh organizer event context.
    func refresh() {
        bridge?.loadContext()
    }

    /// Создает organizer event draft из текстовой SwiftUI-формы.
    ///
    /// - Parameters:
    ///   - workspaceId: Идентификатор выбранного organizer workspace.
    ///   - venueId: Идентификатор выбранной площадки.
    ///   - hallTemplateId: Идентификатор выбранного hall template.
    ///   - title: Название события.
    ///   - description: Необязательное описание.
    ///   - startsAtIso: ISO timestamp начала.
    ///   - doorsOpenAtIso: ISO timestamp открытия дверей.
    ///   - endsAtIso: ISO timestamp завершения.
    ///   - currency: Валюта события.
    ///   - visibilityKey: Wire-ключ публичности.
    func createEvent(
        workspaceId: String,
        venueId: String,
        hallTemplateId: String,
        title: String,
        description: String,
        startsAtIso: String,
        doorsOpenAtIso: String,
        endsAtIso: String,
        currency: String,
        visibilityKey: String
    ) {
        bridge?.createEvent(
            workspaceId: workspaceId,
            venueId: venueId,
            hallTemplateId: hallTemplateId,
            title: title,
            description: description,
            startsAtIso: startsAtIso,
            doorsOpenAtIso: doorsOpenAtIso.isEmpty ? nil : doorsOpenAtIso,
            endsAtIso: endsAtIso.isEmpty ? nil : endsAtIso,
            currency: currency,
            visibilityKey: visibilityKey
        )
    }

    /// Обновляет organizer event details и event-local overrides из SwiftUI editor-а.
    ///
    /// - Parameters:
    ///   - eventId: Идентификатор редактируемого события.
    ///   - title: Название события.
    ///   - description: Необязательное описание.
    ///   - startsAtIso: ISO timestamp начала.
    ///   - doorsOpenAtIso: ISO timestamp открытия дверей.
    ///   - endsAtIso: ISO timestamp завершения.
    ///   - currency: Валюта события.
    ///   - visibilityKey: Wire-ключ публичности.
    ///   - priceZonesText: Текст event-local price zones.
    ///   - pricingAssignmentsText: Текст pricing assignments.
    ///   - availabilityOverridesText: Текст availability overrides.
    func updateEvent(
        eventId: String,
        title: String,
        description: String,
        startsAtIso: String,
        doorsOpenAtIso: String,
        endsAtIso: String,
        currency: String,
        visibilityKey: String,
        priceZonesText: String,
        pricingAssignmentsText: String,
        availabilityOverridesText: String
    ) {
        bridge?.updateEvent(
            eventId: eventId,
            title: title,
            description: description,
            startsAtIso: startsAtIso,
            doorsOpenAtIso: doorsOpenAtIso.isEmpty ? nil : doorsOpenAtIso,
            endsAtIso: endsAtIso.isEmpty ? nil : endsAtIso,
            currency: currency,
            visibilityKey: visibilityKey,
            priceZonesText: priceZonesText,
            pricingAssignmentsText: pricingAssignmentsText,
            availabilityOverridesText: availabilityOverridesText
        )
    }

    /// Публикует существующий organizer event draft.
    ///
    /// - Parameter eventId: Идентификатор события.
    func publishEvent(eventId: String) {
        bridge?.publishEvent(eventId: eventId)
    }

    /// Открывает продажи опубликованного organizer event.
    ///
    /// - Parameter eventId: Идентификатор события.
    func openEventSales(eventId: String) {
        bridge?.openEventSales(eventId: eventId)
    }

    /// Ставит активные продажи organizer event на паузу.
    ///
    /// - Parameter eventId: Идентификатор события.
    func pauseEventSales(eventId: String) {
        bridge?.pauseEventSales(eventId: eventId)
    }

    /// Отменяет organizer event.
    ///
    /// - Parameter eventId: Идентификатор события.
    func cancelEvent(eventId: String) {
        bridge?.cancelEvent(eventId: eventId)
    }

    /// Очищает текущую event error.
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
    /// - Parameter snapshot: Снимок organizer event feature из KMP-слоя.
    @MainActor
    private func apply(snapshot: EventStateSnapshot) {
        events = snapshot.events.map(EventItem.init(snapshot:))
        venues = snapshot.venues.map(EventVenueItem.init(snapshot:))
        isLoading = snapshot.isLoading
        isSubmitting = snapshot.isSubmitting
        errorMessage = snapshot.errorMessage
    }

    /// Применяет fixture-состояние к published-полям без реального backend bridge.
    ///
    /// - Parameter fixture: Локальная fixture event feature.
    private func apply(fixture: EventScreenFixture) {
        events = fixture.events
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

/// Fixture organizer event feature для превью и UI-тестов.
struct EventScreenFixture {
    /// События, которые нужно показать во fixture-режиме.
    let events: [EventItem]

    /// Площадки и шаблоны для формы события.
    let venues: [EventVenueItem]

    /// Признак активной загрузки данных.
    let isLoading: Bool

    /// Признак активной мутации.
    let isSubmitting: Bool

    /// Текст безопасной ошибки.
    let errorMessage: String?

    /// Возвращает типовую organizer fixture с draft, published и on-sale событиями.
    ///
    /// - Parameter workspaceId: Workspace, к которому привязана fixture.
    static func preview(workspaceId: String = "ws-1") -> EventScreenFixture {
        let template = EventTemplateItem(
            id: "template-1",
            name: "Late Layout",
            version: 2,
            statusKey: "published"
        )
        let venue = EventVenueItem(
            id: "venue-1",
            workspaceId: workspaceId,
            name: "Moscow Cellar",
            hallTemplates: [template]
        )
        return EventScreenFixture(
            events: [
                EventItem(
                    id: "event-1",
                    workspaceId: workspaceId,
                    venueId: venue.id,
                    venueName: venue.name,
                    hallSnapshotId: "snapshot-1",
                    sourceTemplateId: template.id,
                    sourceTemplateName: template.name,
                    title: "Late Night Standup",
                    description: "Проверка event foundation",
                    startsAtIso: "2026-04-01T19:00:00+03:00",
                    doorsOpenAtIso: "2026-04-01T18:30:00+03:00",
                    endsAtIso: "2026-04-01T21:00:00+03:00",
                    statusKey: "draft",
                    salesStatusKey: "closed",
                    currency: "RUB",
                    visibilityKey: "public",
                    layoutSummary: "rows=1 · zones=0 · tables=0",
                    overrideSummaryText: "price zones: 0 · pricing: 0 · availability: 0",
                    targetHintText: "row: row-a · seat: row-a-1, row-a-2",
                    priceZonesText: "",
                    pricingAssignmentsText: "",
                    availabilityOverridesText: ""
                ),
                EventItem(
                    id: "event-2",
                    workspaceId: workspaceId,
                    venueId: venue.id,
                    venueName: venue.name,
                    hallSnapshotId: "snapshot-2",
                    sourceTemplateId: template.id,
                    sourceTemplateName: template.name,
                    title: "Published Sales Closed",
                    description: "Событие готово к открытию продаж",
                    startsAtIso: "2026-04-02T19:00:00+03:00",
                    doorsOpenAtIso: "2026-04-02T18:30:00+03:00",
                    endsAtIso: "2026-04-02T21:00:00+03:00",
                    statusKey: "published",
                    salesStatusKey: "closed",
                    currency: "RUB",
                    visibilityKey: "public",
                    layoutSummary: "rows=1 · zones=0 · tables=0",
                    overrideSummaryText: "price zones: 1 · pricing: 1 · availability: 0",
                    targetHintText: "row: row-a · seat: row-a-1, row-a-2",
                    priceZonesText: "event-main|Main|2500|RUB",
                    pricingAssignmentsText: "row|row-a|event-main",
                    availabilityOverridesText: ""
                ),
                EventItem(
                    id: "event-3",
                    workspaceId: workspaceId,
                    venueId: venue.id,
                    venueName: venue.name,
                    hallSnapshotId: "snapshot-3",
                    sourceTemplateId: template.id,
                    sourceTemplateName: template.name,
                    title: "Published Sales Open",
                    description: "Событие с активными продажами",
                    startsAtIso: "2026-04-03T19:00:00+03:00",
                    doorsOpenAtIso: "2026-04-03T18:30:00+03:00",
                    endsAtIso: "2026-04-03T21:00:00+03:00",
                    statusKey: "published",
                    salesStatusKey: "open",
                    currency: "RUB",
                    visibilityKey: "public",
                    layoutSummary: "rows=1 · zones=0 · tables=0",
                    overrideSummaryText: "price zones: 1 · pricing: 1 · availability: 1",
                    targetHintText: "row: row-a · seat: row-a-1, row-a-2",
                    priceZonesText: "event-vip|VIP|3500|RUB",
                    pricingAssignmentsText: "row|row-a|event-vip",
                    availabilityOverridesText: "seat|row-a-2|blocked"
                )
            ],
            venues: [venue],
            isLoading: false,
            isSubmitting: false,
            errorMessage: nil
        )
    }
}

/// Organizer event для SwiftUI-слоя.
struct EventItem: Identifiable {
    /// Уникальный идентификатор события.
    let id: String

    /// Workspace-владелец события.
    let workspaceId: String

    /// Площадка события.
    let venueId: String

    /// Frozen имя площадки.
    let venueName: String

    /// Frozen hall snapshot id.
    let hallSnapshotId: String

    /// Исходный hall template id.
    let sourceTemplateId: String

    /// Frozen имя исходного hall template.
    let sourceTemplateName: String

    /// Название события.
    let title: String

    /// Необязательное описание.
    let description: String?

    /// Время старта в ISO формате.
    let startsAtIso: String

    /// Время открытия дверей.
    let doorsOpenAtIso: String?

    /// Время окончания события.
    let endsAtIso: String?

    /// Wire-ключ статуса события.
    let statusKey: String

    /// Wire-ключ sales status.
    let salesStatusKey: String

    /// Валюта события.
    let currency: String

    /// Wire-ключ visibility.
    let visibilityKey: String

    /// Краткая сводка frozen layout.
    let layoutSummary: String

    /// Краткая сводка event-local override state.
    let overrideSummaryText: String

    /// Подсказка по доступным snapshot targets.
    let targetHintText: String

    /// Текст event-local price zones.
    let priceZonesText: String

    /// Текст pricing assignments.
    let pricingAssignmentsText: String

    /// Текст availability overrides.
    let availabilityOverridesText: String

    /// Возвращает `true`, если событие еще допускает organizer-side редактирование.
    var isEditable: Bool {
        statusKey != "canceled"
    }

    /// Возвращает `true`, если для события можно открыть или возобновить продажи.
    var canOpenSales: Bool {
        statusKey == "published" && (salesStatusKey == "closed" || salesStatusKey == "paused")
    }

    /// Возвращает `true`, если активные продажи можно поставить на паузу.
    var canPauseSales: Bool {
        statusKey == "published" && salesStatusKey == "open"
    }

    /// Возвращает `true`, если событие можно отменить из текущего organizer lifecycle.
    var canCancel: Bool {
        statusKey == "draft" ||
            (statusKey == "published" &&
                (salesStatusKey == "closed" || salesStatusKey == "open" || salesStatusKey == "paused"))
    }

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot события из общего bridge.
    init(snapshot: EventSnapshot) {
        id = snapshot.id
        workspaceId = snapshot.workspaceId
        venueId = snapshot.venueId
        venueName = snapshot.venueName
        hallSnapshotId = snapshot.hallSnapshotId
        sourceTemplateId = snapshot.sourceTemplateId
        sourceTemplateName = snapshot.sourceTemplateName
        title = snapshot.title
        description = snapshot.description_
        startsAtIso = snapshot.startsAtIso
        doorsOpenAtIso = snapshot.doorsOpenAtIso
        endsAtIso = snapshot.endsAtIso
        statusKey = snapshot.statusKey
        salesStatusKey = snapshot.salesStatusKey
        currency = snapshot.currency
        visibilityKey = snapshot.visibilityKey
        layoutSummary = "rows=\(snapshot.layoutRowCount) · zones=\(snapshot.layoutZoneCount) · tables=\(snapshot.layoutTableCount)"
        overrideSummaryText = snapshot.overrideSummaryText
        targetHintText = snapshot.targetHintText
        priceZonesText = snapshot.priceZonesText
        pricingAssignmentsText = snapshot.pricingAssignmentsText
        availabilityOverridesText = snapshot.availabilityOverridesText
    }

    /// Явный инициализатор для fixture-сценариев.
    init(
        id: String,
        workspaceId: String,
        venueId: String,
        venueName: String,
        hallSnapshotId: String,
        sourceTemplateId: String,
        sourceTemplateName: String,
        title: String,
        description: String?,
        startsAtIso: String,
        doorsOpenAtIso: String?,
        endsAtIso: String?,
        statusKey: String,
        salesStatusKey: String,
        currency: String,
        visibilityKey: String,
        layoutSummary: String,
        overrideSummaryText: String,
        targetHintText: String,
        priceZonesText: String,
        pricingAssignmentsText: String,
        availabilityOverridesText: String
    ) {
        self.id = id
        self.workspaceId = workspaceId
        self.venueId = venueId
        self.venueName = venueName
        self.hallSnapshotId = hallSnapshotId
        self.sourceTemplateId = sourceTemplateId
        self.sourceTemplateName = sourceTemplateName
        self.title = title
        self.description = description
        self.startsAtIso = startsAtIso
        self.doorsOpenAtIso = doorsOpenAtIso
        self.endsAtIso = endsAtIso
        self.statusKey = statusKey
        self.salesStatusKey = salesStatusKey
        self.currency = currency
        self.visibilityKey = visibilityKey
        self.layoutSummary = layoutSummary
        self.overrideSummaryText = overrideSummaryText
        self.targetHintText = targetHintText
        self.priceZonesText = priceZonesText
        self.pricingAssignmentsText = pricingAssignmentsText
        self.availabilityOverridesText = availabilityOverridesText
    }
}

/// Organizer venue option для SwiftUI event form.
struct EventVenueItem: Identifiable {
    /// Уникальный идентификатор площадки.
    let id: String

    /// Workspace-владелец площадки.
    let workspaceId: String

    /// Название площадки.
    let name: String

    /// Доступные hall templates этой площадки.
    let hallTemplates: [EventTemplateItem]

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot площадки из общего bridge.
    init(snapshot: EventVenueOptionSnapshot) {
        id = snapshot.id
        workspaceId = snapshot.workspaceId
        name = snapshot.name
        hallTemplates = snapshot.hallTemplates.map(EventTemplateItem.init(snapshot:))
    }

    /// Явный инициализатор для fixture-сценариев.
    init(id: String, workspaceId: String, name: String, hallTemplates: [EventTemplateItem]) {
        self.id = id
        self.workspaceId = workspaceId
        self.name = name
        self.hallTemplates = hallTemplates
    }
}

/// Organizer hall template option для SwiftUI event form.
struct EventTemplateItem: Identifiable {
    /// Уникальный идентификатор hall template.
    let id: String

    /// Название hall template.
    let name: String

    /// Версия hall template.
    let version: Int

    /// Wire-ключ статуса hall template.
    let statusKey: String

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot шаблона из общего bridge.
    init(snapshot: EventTemplateOptionSnapshot) {
        id = snapshot.id
        name = snapshot.name
        version = Int(snapshot.version)
        statusKey = snapshot.statusKey
    }

    /// Явный инициализатор для fixture-сценариев.
    init(id: String, name: String, version: Int, statusKey: String) {
        self.id = id
        self.name = name
        self.version = version
        self.statusKey = statusKey
    }
}
