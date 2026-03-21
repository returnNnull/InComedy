import Foundation
import Shared

/// SwiftUI-модель audience/staff ticketing feature.
///
/// Модель адаптирует общий `TicketingBridge` к локальным published-полям и не дает SwiftUI
/// зависеть от Kotlin snapshot-типов напрямую.
final class TicketWalletModel: ObservableObject {
    /// Билеты текущего пользователя для экрана `Мои билеты`.
    @Published var tickets: [TicketItem] = []

    /// Показывает загрузку списка билетов.
    @Published var isLoading: Bool = false

    /// Показывает активную server-side проверку QR payload.
    @Published var isScanning: Bool = false

    /// Хранит текст безопасной ticketing-ошибки.
    @Published var errorMessage: String?

    /// Хранит последний результат check-in, если он есть.
    @Published var lastCheckInResult: TicketCheckInOutcomeItem?

    /// Bridge к общей ticketing feature model.
    private let bridge: TicketingBridge?

    /// Удерживает текущую подписку на Kotlin state updates.
    private var bindingHandle: NSObject?

    /// Создает живую SwiftUI-модель и сразу синхронизирует состояние из общего KMP-слоя.
    ///
    /// - Parameter bridge: Необязательный bridge для тестов.
    init(bridge: TicketingBridge? = nil) {
        self.bridge = bridge ?? TicketingBridge(
            viewModel: InComedyKoin.shared.getTicketingViewModel()
        )
        bind()
        self.bridge?.loadTickets()
    }

    /// Создает fixture-версию модели без подключения к реальному bridge.
    ///
    /// - Parameter fixture: Предзагруженное состояние для превью и UI-тестов.
    init(fixture: TicketWalletFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    /// Выполняет ручной refresh списка билетов.
    func refresh() {
        bridge?.loadTickets()
    }

    /// Отправляет QR payload на server-side проверку.
    ///
    /// - Parameter qrPayload: Непрозрачная строка, прочитанная из QR.
    func scanTicket(qrPayload: String) {
        let normalizedPayload = qrPayload.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.scanTicket(qrPayload: normalizedPayload)
            return
        }

        if normalizedPayload.isEmpty {
            errorMessage = "Введите QR payload для проверки билета"
            return
        }

        guard let index = tickets.firstIndex(where: { $0.qrPayload == normalizedPayload }) else {
            errorMessage = "Билет не найден"
            return
        }

        if tickets[index].statusKey == "checked_in" {
            lastCheckInResult = TicketCheckInOutcomeItem(
                resultCodeKey: "duplicate",
                ticket: tickets[index]
            )
            return
        }

        tickets[index].statusKey = "checked_in"
        tickets[index].checkedInAtIso = "2026-04-01T18:59:00+03:00"
        tickets[index].checkedInByUserId = "checker-1"
        lastCheckInResult = TicketCheckInOutcomeItem(
            resultCodeKey: "checked_in",
            ticket: tickets[index]
        )
    }

    /// Очищает текущую ticketing error.
    func clearError() {
        if let bridge {
            bridge.clearError()
        } else {
            errorMessage = nil
        }
    }

    /// Очищает последний результат проверки QR.
    func clearCheckInResult() {
        if let bridge {
            bridge.clearCheckInResult()
        } else {
            lastCheckInResult = nil
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
    /// - Parameter snapshot: Снимок ticketing feature из KMP-слоя.
    @MainActor
    private func apply(snapshot: TicketingStateSnapshot) {
        tickets = snapshot.tickets.map(TicketItem.init(snapshot:))
        isLoading = snapshot.isLoading
        isScanning = snapshot.isScanning
        errorMessage = snapshot.errorMessage
        lastCheckInResult = snapshot.lastCheckInResult.map(TicketCheckInOutcomeItem.init(snapshot:))
    }

    /// Применяет fixture-состояние к published-полям без реального backend bridge.
    ///
    /// - Parameter fixture: Локальная fixture ticketing feature.
    private func apply(fixture: TicketWalletFixture) {
        tickets = fixture.tickets
        isLoading = fixture.isLoading
        isScanning = fixture.isScanning
        errorMessage = fixture.errorMessage
        lastCheckInResult = fixture.lastCheckInResult
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

/// Fixture ticketing feature для превью и UI-тестов.
struct TicketWalletFixture {
    /// Билеты, которые нужно показать во fixture-режиме.
    let tickets: [TicketItem]

    /// Признак активной загрузки списка.
    let isLoading: Bool

    /// Признак активной server-side проверки QR.
    let isScanning: Bool

    /// Текст безопасной ошибки.
    let errorMessage: String?

    /// Последний результат check-in, если нужно показать его сразу.
    let lastCheckInResult: TicketCheckInOutcomeItem?

    /// Возвращает типовую fixture для билетов и staff check-in.
    static func preview() -> TicketWalletFixture {
        TicketWalletFixture(
            tickets: [
                TicketItem(
                    id: "ticket-1",
                    orderId: "order-1",
                    eventId: "event-1",
                    inventoryUnitId: "inventory-1",
                    inventoryRef: "seat-a1",
                    label: "Ряд A · Место 1",
                    statusKey: "issued",
                    qrPayload: "incomedy.ticket.v1:ticket-1",
                    issuedAtIso: "2026-04-01T18:45:00+03:00",
                    checkedInAtIso: nil,
                    checkedInByUserId: nil
                ),
                TicketItem(
                    id: "ticket-2",
                    orderId: "order-2",
                    eventId: "event-2",
                    inventoryUnitId: "inventory-2",
                    inventoryRef: "table-b2-seat-3",
                    label: "Стол B2 · Место 3",
                    statusKey: "checked_in",
                    qrPayload: "incomedy.ticket.v1:ticket-2",
                    issuedAtIso: "2026-04-02T18:00:00+03:00",
                    checkedInAtIso: "2026-04-02T18:59:00+03:00",
                    checkedInByUserId: "checker-1"
                )
            ],
            isLoading: false,
            isScanning: false,
            errorMessage: nil,
            lastCheckInResult: nil
        )
    }
}

/// Модель билета для SwiftUI-слоя.
struct TicketItem: Identifiable {
    /// Уникальный идентификатор билета.
    let id: String

    /// Идентификатор заказа-владельца.
    let orderId: String

    /// Идентификатор события.
    let eventId: String

    /// Идентификатор inventory unit.
    let inventoryUnitId: String

    /// Стабильная ссылка на место/слот.
    let inventoryRef: String

    /// Человекочитаемая подпись билета.
    let label: String

    /// Текущий wire-статус билета.
    var statusKey: String

    /// Непрозрачный payload для QR-кода.
    let qrPayload: String?

    /// Момент выпуска билета.
    let issuedAtIso: String

    /// Момент фактического прохода, если он уже был.
    var checkedInAtIso: String?

    /// Идентификатор сотрудника, выполнившего check-in.
    var checkedInByUserId: String?

    /// Создает SwiftUI-модель из общего snapshot.
    ///
    /// - Parameter snapshot: Снимок билета из Kotlin bridge.
    init(snapshot: TicketSnapshot) {
        id = snapshot.id
        orderId = snapshot.orderId
        eventId = snapshot.eventId
        inventoryUnitId = snapshot.inventoryUnitId
        inventoryRef = snapshot.inventoryRef
        label = snapshot.label
        statusKey = snapshot.statusKey
        qrPayload = snapshot.qrPayload
        issuedAtIso = snapshot.issuedAtIso
        checkedInAtIso = snapshot.checkedInAtIso
        checkedInByUserId = snapshot.checkedInByUserId
    }

    /// Создает fixture-модель без bridge.
    init(
        id: String,
        orderId: String,
        eventId: String,
        inventoryUnitId: String,
        inventoryRef: String,
        label: String,
        statusKey: String,
        qrPayload: String?,
        issuedAtIso: String,
        checkedInAtIso: String?,
        checkedInByUserId: String?
    ) {
        self.id = id
        self.orderId = orderId
        self.eventId = eventId
        self.inventoryUnitId = inventoryUnitId
        self.inventoryRef = inventoryRef
        self.label = label
        self.statusKey = statusKey
        self.qrPayload = qrPayload
        self.issuedAtIso = issuedAtIso
        self.checkedInAtIso = checkedInAtIso
        self.checkedInByUserId = checkedInByUserId
    }
}

/// Модель результата staff check-in для SwiftUI-слоя.
struct TicketCheckInOutcomeItem {
    /// Wire-код результата проверки.
    let resultCodeKey: String

    /// Актуальное состояние билета после обработки.
    let ticket: TicketItem

    /// Создает SwiftUI-модель из общего snapshot.
    ///
    /// - Parameter snapshot: Снимок результата из Kotlin bridge.
    init(snapshot: TicketCheckInResultSnapshot) {
        resultCodeKey = snapshot.resultCodeKey
        ticket = TicketItem(snapshot: snapshot.ticket)
    }

    /// Создает fixture-модель без bridge.
    init(
        resultCodeKey: String,
        ticket: TicketItem
    ) {
        self.resultCodeKey = resultCodeKey
        self.ticket = ticket
    }
}
