import Foundation
import Shared

/// SwiftUI-модель organizer announcements и audience-safe event feed surface.
final class AnnouncementFeedModel: ObservableObject {
    @Published var selectedEventId: String?
    @Published var announcements: [EventAnnouncementItem] = []
    @Published var isLoading: Bool = false
    @Published var isSubmitting: Bool = false
    @Published var errorMessage: String?

    private let bridge: NotificationsBridge?
    private var bindingHandle: NSObject?

    init(bridge: NotificationsBridge? = nil) {
        self.bridge = bridge ?? NotificationsBridge(
            viewModel: InComedyKoin.shared.getNotificationsViewModel()
        )
        bind()
    }

    init(fixture: AnnouncementFeedFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    func loadAnnouncements(eventId: String) {
        let normalizedEventId = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.loadAnnouncements(eventId: normalizedEventId)
            return
        }

        guard !normalizedEventId.isEmpty else {
            errorMessage = "Не выбран event для загрузки announcement feed"
            return
        }

        let previousEventId = selectedEventId
        selectedEventId = normalizedEventId
        if previousEventId != normalizedEventId || announcements.isEmpty {
            announcements = AnnouncementFeedFixture.preview(eventId: normalizedEventId).announcements
        }
        errorMessage = nil
    }

    func refresh() {
        guard let selectedEventId else { return }
        loadAnnouncements(eventId: selectedEventId)
    }

    func createAnnouncement(eventId: String, message: String) {
        let normalizedEventId = eventId.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedMessage = message.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.createAnnouncement(
                eventId: normalizedEventId,
                message: normalizedMessage
            )
            return
        }

        guard !normalizedEventId.isEmpty else {
            errorMessage = "Не выбран event для публикации announcement-а"
            return
        }
        guard !normalizedMessage.isEmpty else {
            errorMessage = "Текст announcement-а не должен быть пустым"
            return
        }
        guard normalizedMessage.count <= 1000 else {
            errorMessage = "Announcement message должен быть не длиннее 1000 символов"
            return
        }

        selectedEventId = normalizedEventId
        announcements.insert(
            EventAnnouncementItem(
                id: "announcement-local-\(announcements.count + 1)",
                eventId: normalizedEventId,
                message: normalizedMessage,
                authorRoleKey: "organizer",
                createdAtIso: "2026-03-27T19:10:00+03:00"
            ),
            at: 0
        )
        errorMessage = nil
    }

    func clearError() {
        if let bridge {
            bridge.clearError()
        } else {
            errorMessage = nil
        }
    }

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

    @MainActor
    private func apply(snapshot: NotificationsStateSnapshot) {
        selectedEventId = snapshot.selectedEventId
        announcements = snapshot.announcements.map(EventAnnouncementItem.init(snapshot:))
        isLoading = snapshot.isLoading
        isSubmitting = snapshot.isSubmitting
        errorMessage = snapshot.errorMessage
    }

    private func apply(fixture: AnnouncementFeedFixture) {
        selectedEventId = fixture.selectedEventId
        announcements = fixture.announcements
        isLoading = fixture.isLoading
        isSubmitting = fixture.isSubmitting
        errorMessage = fixture.errorMessage
    }

    private func setBinding(_ handle: Any) {
        disposeBindingIfNeeded()
        bindingHandle = handle as? NSObject
    }

    private func disposeBindingIfNeeded() {
        guard let bindingHandle else { return }
        let disposeSelector = NSSelectorFromString("dispose")
        if bindingHandle.responds(to: disposeSelector) {
            _ = bindingHandle.perform(disposeSelector)
        }
        self.bindingHandle = nil
    }
}

struct AnnouncementFeedFixture {
    let selectedEventId: String?
    let announcements: [EventAnnouncementItem]
    let isLoading: Bool
    let isSubmitting: Bool
    let errorMessage: String?

    static func preview(eventId: String = "event-2") -> AnnouncementFeedFixture {
        AnnouncementFeedFixture(
            selectedEventId: eventId,
            announcements: [
                EventAnnouncementItem(
                    id: "announcement-1",
                    eventId: eventId,
                    message: "Начинаем через 10 минут",
                    authorRoleKey: "organizer",
                    createdAtIso: "2026-03-27T19:00:00+03:00"
                )
            ],
            isLoading: false,
            isSubmitting: false,
            errorMessage: nil
        )
    }
}

struct EventAnnouncementItem: Identifiable {
    let id: String
    let eventId: String
    let message: String
    let authorRoleKey: String
    let createdAtIso: String

    init(snapshot: EventAnnouncementSnapshot) {
        id = snapshot.id
        eventId = snapshot.eventId
        message = snapshot.message
        authorRoleKey = snapshot.authorRoleKey
        createdAtIso = snapshot.createdAtIso
    }

    init(
        id: String,
        eventId: String,
        message: String,
        authorRoleKey: String,
        createdAtIso: String
    ) {
        self.id = id
        self.eventId = eventId
        self.message = message
        self.authorRoleKey = authorRoleKey
        self.createdAtIso = createdAtIso
    }
}
