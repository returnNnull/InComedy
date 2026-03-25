import Foundation
import Shared

/// SwiftUI-модель comedian applications и organizer lineup feature.
///
/// Модель адаптирует общий `LineupBridge` к локальным published-полям и не дает SwiftUI зависеть
/// от Kotlin snapshot-типов напрямую.
final class LineupScreenModel: ObservableObject {
    /// Идентификатор события, для которого сейчас загружен organizer context.
    @Published var selectedEventId: String?

    /// Список comedian applications текущего organizer context-а.
    @Published var applications: [ComedianApplicationItem] = []

    /// Упорядоченный список lineup entries.
    @Published var lineup: [LineupEntryItem] = []

    /// Показывает загрузку organizer context.
    @Published var isLoading: Bool = false

    /// Показывает активную submit/review/reorder мутацию.
    @Published var isSubmitting: Bool = false

    /// Хранит верхнеуровневую безопасную ошибку feature-а.
    @Published var errorMessage: String?

    /// Возвращает текущего комика на сцене из актуального lineup.
    var currentPerformer: LineupEntryItem? {
        lineup.first(where: { $0.statusKey == "on_stage" })
    }

    /// Возвращает ближайшего следующего комика из актуального lineup.
    var nextUpPerformer: LineupEntryItem? {
        lineup.first(where: { $0.statusKey == "up_next" })
    }

    /// Bridge к общей lineup feature model.
    private let bridge: LineupBridge?

    /// Удерживает текущую подписку на Kotlin state updates.
    private var bindingHandle: NSObject?

    /// Создает живую SwiftUI-модель и сразу синхронизирует состояние из общего KMP-слоя.
    ///
    /// - Parameter bridge: Необязательный bridge для тестов.
    init(bridge: LineupBridge? = nil) {
        self.bridge = bridge ?? LineupBridge(
            viewModel: InComedyKoin.shared.getLineupViewModel()
        )
        bind()
    }

    /// Создает fixture-версию модели без подключения к реальному bridge.
    ///
    /// - Parameter fixture: Предзагруженное состояние для превью и UI-тестов.
    init(fixture: LineupScreenFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    /// Загружает organizer applications и lineup для выбранного события.
    ///
    /// - Parameter eventId: Идентификатор organizer event.
    func loadOrganizerContext(eventId: String) {
        let normalizedEventId = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.loadOrganizerContext(eventId: normalizedEventId)
        } else {
            selectedEventId = normalizedEventId
        }
    }

    /// Отправляет comedian application на выбранное событие.
    ///
    /// - Parameters:
    ///   - eventId: Идентификатор события.
    ///   - note: Необязательная заметка к заявке.
    func submitApplication(eventId: String, note: String?) {
        let normalizedEventId = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedNote = note?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.submitApplication(
                eventId: normalizedEventId,
                note: normalizedNote?.isEmpty == true ? nil : normalizedNote
            )
            return
        }

        guard !normalizedEventId.isEmpty else {
            errorMessage = "Укажите event id для заявки"
            return
        }

        selectedEventId = normalizedEventId
        applications.insert(
            ComedianApplicationItem(
                id: "application-local-\(applications.count + 1)",
                eventId: normalizedEventId,
                comedianUserId: "comedian-local",
                comedianDisplayName: "Тестовый комик",
                comedianUsername: "fixture_comedian",
                statusKey: "submitted",
                note: normalizedNote,
                reviewedByUserId: nil,
                reviewedByDisplayName: nil,
                createdAtIso: "2026-03-23T10:40:00+03:00",
                updatedAtIso: "2026-03-23T10:40:00+03:00",
                statusUpdatedAtIso: "2026-03-23T10:40:00+03:00"
            ),
            at: 0
        )
    }

    /// Меняет review-статус organizer-side заявки.
    ///
    /// - Parameters:
    ///   - eventId: Идентификатор события.
    ///   - applicationId: Идентификатор заявки.
    ///   - statusKey: Wire-ключ нового статуса.
    func updateApplicationStatus(
        eventId: String,
        applicationId: String,
        statusKey: String
    ) {
        if let bridge {
            bridge.updateApplicationStatus(
                eventId: eventId,
                applicationId: applicationId,
                statusKey: statusKey
            )
            return
        }

        guard let index = applications.firstIndex(where: { $0.id == applicationId }) else { return }

        // Явно переустанавливаем массив, чтобы fixture-режим гарантированно публиковал SwiftUI-обновление.
        var updatedApplications = applications
        updatedApplications[index].statusKey = statusKey
        updatedApplications[index].reviewedByUserId = "owner-1"
        updatedApplications[index].reviewedByDisplayName = "Организатор"
        updatedApplications[index].updatedAtIso = "2026-03-23T10:41:00+03:00"
        updatedApplications[index].statusUpdatedAtIso = "2026-03-23T10:41:00+03:00"
        applications = updatedApplications

        if statusKey == "approved" && lineup.allSatisfy({ $0.applicationId != applicationId }) {
            var updatedLineup = lineup
            updatedLineup.append(
                LineupEntryItem(
                    id: "entry-local-\(updatedLineup.count + 1)",
                    eventId: eventId,
                    comedianUserId: updatedApplications[index].comedianUserId,
                    comedianDisplayName: updatedApplications[index].comedianDisplayName,
                    comedianUsername: updatedApplications[index].comedianUsername,
                    applicationId: applicationId,
                    orderIndex: updatedLineup.count + 1,
                    statusKey: "draft",
                    notes: nil,
                    createdAtIso: "2026-03-23T10:41:00+03:00",
                    updatedAtIso: "2026-03-23T10:41:00+03:00"
                )
            )
            lineup = updatedLineup
        }
    }

    /// Смещает lineup entry на соседнюю позицию и отправляет полный новый порядок.
    ///
    /// - Parameters:
    ///   - eventId: Идентификатор события.
    ///   - entryId: Идентификатор смещаемой записи.
    ///   - delta: Смещение вверх `-1` или вниз `+1`.
    func moveLineupEntry(
        eventId: String,
        entryId: String,
        delta: Int
    ) {
        guard let reorderedIds = reorderedEntryIds(
            entries: lineup,
            entryId: entryId,
            delta: delta
        ) else {
            return
        }
        if let bridge {
            bridge.reorderLineup(
                eventId: eventId,
                orderedEntryIds: reorderedIds
            )
            return
        }

        lineup = reorderedIds.enumerated().compactMap { index, entryId in
            lineup.first(where: { $0.id == entryId })?.withOrderIndex(index + 1)
        }
    }

    /// Меняет live-stage статус конкретной записи lineup.
    ///
    /// - Parameters:
    ///   - eventId: Идентификатор события.
    ///   - entryId: Идентификатор записи lineup.
    ///   - statusKey: Wire-ключ нового live-stage статуса.
    func updateLineupEntryStatus(
        eventId: String,
        entryId: String,
        statusKey: String
    ) {
        if let bridge {
            bridge.updateLineupEntryStatus(
                eventId: eventId,
                entryId: entryId,
                statusKey: statusKey
            )
            return
        }

        lineup = lineup.map { item in
            if item.id == entryId {
                return item.withStatusKey(statusKey)
            }
            if statusKey == "on_stage", item.statusKey == "on_stage" {
                return item.withStatusKey("draft")
            }
            if statusKey == "up_next", item.statusKey == "up_next" {
                return item.withStatusKey("draft")
            }
            return item
        }
    }

    /// Очищает текущую lineup error.
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
    /// - Parameter snapshot: Снимок lineup feature из KMP-слоя.
    @MainActor
    private func apply(snapshot: LineupStateSnapshot) {
        selectedEventId = snapshot.selectedEventId
        applications = snapshot.applications.map(ComedianApplicationItem.init(snapshot:))
        lineup = snapshot.lineup.map(LineupEntryItem.init(snapshot:))
        isLoading = snapshot.isLoading
        isSubmitting = snapshot.isSubmitting
        errorMessage = snapshot.errorMessage
    }

    /// Применяет fixture-состояние к published-полям без реального backend bridge.
    ///
    /// - Parameter fixture: Локальная fixture lineup feature.
    private func apply(fixture: LineupScreenFixture) {
        selectedEventId = fixture.selectedEventId
        applications = fixture.applications
        lineup = fixture.lineup
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

/// Fixture lineup feature для превью и UI-тестов.
struct LineupScreenFixture {
    /// Выбранный organizer event fixture-состояния.
    let selectedEventId: String?

    /// Заявки, которые нужно показать в fixture-режиме.
    let applications: [ComedianApplicationItem]

    /// Lineup entries, которые нужно показать в fixture-режиме.
    let lineup: [LineupEntryItem]

    /// Признак активной загрузки organizer context.
    let isLoading: Bool

    /// Признак активной мутации.
    let isSubmitting: Bool

    /// Текст безопасной ошибки.
    let errorMessage: String?

    /// Возвращает типовую fixture для applications и lineup.
    ///
    /// - Parameter eventId: Event, к которому относится fixture.
    static func preview(eventId: String = "event-1") -> LineupScreenFixture {
        return LineupScreenFixture(
            selectedEventId: eventId,
            applications: [
                ComedianApplicationItem(
                    id: "application-1",
                    eventId: eventId,
                    comedianUserId: "comedian-1",
                    comedianDisplayName: "Иван Смехов",
                    comedianUsername: "smile",
                    statusKey: "submitted",
                    note: "Новый пятиминутный сет",
                    reviewedByUserId: nil,
                    reviewedByDisplayName: nil,
                    createdAtIso: "2026-03-23T01:00:00+03:00",
                    updatedAtIso: "2026-03-23T01:00:00+03:00",
                    statusUpdatedAtIso: "2026-03-23T01:00:00+03:00"
                )
            ],
            lineup: [
                LineupEntryItem(
                    id: "entry-1",
                    eventId: eventId,
                    comedianUserId: "comedian-1",
                    comedianDisplayName: "Иван Смехов",
                    comedianUsername: "smile",
                    applicationId: "application-approved",
                    orderIndex: 1,
                    statusKey: "on_stage",
                    notes: nil,
                    createdAtIso: "2026-03-23T01:10:00+03:00",
                    updatedAtIso: "2026-03-23T01:10:00+03:00"
                ),
                LineupEntryItem(
                    id: "entry-2",
                    eventId: eventId,
                    comedianUserId: "comedian-2",
                    comedianDisplayName: "Мария Сетова",
                    comedianUsername: "mset",
                    applicationId: "application-approved-2",
                    orderIndex: 2,
                    statusKey: "up_next",
                    notes: nil,
                    createdAtIso: "2026-03-23T01:20:00+03:00",
                    updatedAtIso: "2026-03-23T01:20:00+03:00"
                )
            ],
            isLoading: false,
            isSubmitting: false,
            errorMessage: nil
        )
    }
}

/// SwiftUI-модель comedian application.
struct ComedianApplicationItem: Identifiable {
    /// Уникальный идентификатор заявки.
    let id: String

    /// Идентификатор связанного события.
    let eventId: String

    /// Идентификатор пользователя-комика.
    let comedianUserId: String

    /// Отображаемое имя комика.
    let comedianDisplayName: String

    /// Username комика, если он есть.
    let comedianUsername: String?

    /// Текущий wire-статус заявки.
    var statusKey: String

    /// Необязательная заметка к заявке.
    let note: String?

    /// Reviewer id, если ревью уже было.
    var reviewedByUserId: String?

    /// Отображаемое имя reviewer-а.
    var reviewedByDisplayName: String?

    /// Время создания заявки.
    let createdAtIso: String

    /// Время последнего обновления заявки.
    var updatedAtIso: String

    /// Время последней смены статуса.
    var statusUpdatedAtIso: String

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot заявки из общего bridge.
    init(snapshot: ComedianApplicationSnapshot) {
        id = snapshot.id
        eventId = snapshot.eventId
        comedianUserId = snapshot.comedianUserId
        comedianDisplayName = snapshot.comedianDisplayName
        comedianUsername = snapshot.comedianUsername
        statusKey = snapshot.statusKey
        note = snapshot.note
        reviewedByUserId = snapshot.reviewedByUserId
        reviewedByDisplayName = snapshot.reviewedByDisplayName
        createdAtIso = snapshot.createdAtIso
        updatedAtIso = snapshot.updatedAtIso
        statusUpdatedAtIso = snapshot.statusUpdatedAtIso
    }

    /// Явный инициализатор для fixture-сценариев.
    init(
        id: String,
        eventId: String,
        comedianUserId: String,
        comedianDisplayName: String,
        comedianUsername: String?,
        statusKey: String,
        note: String?,
        reviewedByUserId: String?,
        reviewedByDisplayName: String?,
        createdAtIso: String,
        updatedAtIso: String,
        statusUpdatedAtIso: String
    ) {
        self.id = id
        self.eventId = eventId
        self.comedianUserId = comedianUserId
        self.comedianDisplayName = comedianDisplayName
        self.comedianUsername = comedianUsername
        self.statusKey = statusKey
        self.note = note
        self.reviewedByUserId = reviewedByUserId
        self.reviewedByDisplayName = reviewedByDisplayName
        self.createdAtIso = createdAtIso
        self.updatedAtIso = updatedAtIso
        self.statusUpdatedAtIso = statusUpdatedAtIso
    }
}

/// SwiftUI-модель одного lineup entry.
struct LineupEntryItem: Identifiable {
    /// Уникальный идентификатор записи lineup.
    let id: String

    /// Идентификатор события.
    let eventId: String

    /// Идентификатор пользователя-комика.
    let comedianUserId: String

    /// Отображаемое имя комика.
    let comedianDisplayName: String

    /// Username комика, если он есть.
    let comedianUsername: String?

    /// Ссылка на исходную заявку, если она есть.
    let applicationId: String?

    /// Явная позиция внутри lineup.
    let orderIndex: Int

    /// Wire-статус entry.
    let statusKey: String

    /// Необязательная organizer-заметка.
    let notes: String?

    /// Время создания записи.
    let createdAtIso: String

    /// Время последнего обновления.
    let updatedAtIso: String

    /// Маппит Kotlin snapshot в локальную SwiftUI-модель.
    ///
    /// - Parameter snapshot: Snapshot entry из общего bridge.
    init(snapshot: LineupEntrySnapshot) {
        id = snapshot.id
        eventId = snapshot.eventId
        comedianUserId = snapshot.comedianUserId
        comedianDisplayName = snapshot.comedianDisplayName
        comedianUsername = snapshot.comedianUsername
        applicationId = snapshot.applicationId
        orderIndex = Int(snapshot.orderIndex)
        statusKey = snapshot.statusKey
        notes = snapshot.notes
        createdAtIso = snapshot.createdAtIso
        updatedAtIso = snapshot.updatedAtIso
    }

    /// Явный инициализатор для fixture-сценариев.
    init(
        id: String,
        eventId: String,
        comedianUserId: String,
        comedianDisplayName: String,
        comedianUsername: String?,
        applicationId: String?,
        orderIndex: Int,
        statusKey: String,
        notes: String?,
        createdAtIso: String,
        updatedAtIso: String
    ) {
        self.id = id
        self.eventId = eventId
        self.comedianUserId = comedianUserId
        self.comedianDisplayName = comedianDisplayName
        self.comedianUsername = comedianUsername
        self.applicationId = applicationId
        self.orderIndex = orderIndex
        self.statusKey = statusKey
        self.notes = notes
        self.createdAtIso = createdAtIso
        self.updatedAtIso = updatedAtIso
    }

    /// Возвращает копию entry с обновленным explicit order index.
    ///
    /// - Parameter orderIndex: Новый order index.
    func withOrderIndex(_ orderIndex: Int) -> LineupEntryItem {
        LineupEntryItem(
            id: id,
            eventId: eventId,
            comedianUserId: comedianUserId,
            comedianDisplayName: comedianDisplayName,
            comedianUsername: comedianUsername,
            applicationId: applicationId,
            orderIndex: orderIndex,
            statusKey: statusKey,
            notes: notes,
            createdAtIso: createdAtIso,
            updatedAtIso: updatedAtIso
        )
    }

    /// Возвращает копию entry с обновленным live-stage статусом.
    ///
    /// - Parameter statusKey: Новый wire-статус.
    func withStatusKey(_ statusKey: String) -> LineupEntryItem {
        LineupEntryItem(
            id: id,
            eventId: eventId,
            comedianUserId: comedianUserId,
            comedianDisplayName: comedianDisplayName,
            comedianUsername: comedianUsername,
            applicationId: applicationId,
            orderIndex: orderIndex,
            statusKey: statusKey,
            notes: notes,
            createdAtIso: createdAtIso,
            updatedAtIso: updatedAtIso
        )
    }
}

/// Строит новый полный порядок entry id после соседнего смещения.
///
/// - Parameters:
///   - entries: Текущий lineup.
///   - entryId: Идентификатор смещаемой записи.
///   - delta: Смещение вверх `-1` или вниз `+1`.
/// - Returns: Новый полный порядок id либо `nil`, если перестановка невозможна.
private func reorderedEntryIds(
    entries: [LineupEntryItem],
    entryId: String,
    delta: Int
) -> [String]? {
    guard let sourceIndex = entries.firstIndex(where: { $0.id == entryId }) else {
        return nil
    }
    let targetIndex = sourceIndex + delta
    guard entries.indices.contains(targetIndex) else {
        return nil
    }

    var ids = entries.map(\.id)
    let movedId = ids.remove(at: sourceIndex)
    ids.insert(movedId, at: targetIndex)
    return ids
}
