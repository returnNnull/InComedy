import SwiftUI

/// SwiftUI-экран organizer announcements и audience-safe event feed внутри авторизованного tab shell.
struct AnnouncementFeedView: View {
    @ObservedObject var model: AnnouncementFeedModel

    let events: [EventItem]

    @State private var selectedEventId: String = ""
    @State private var draftMessage: String = ""

    private var eligibleEvents: [EventItem] {
        events.filter { $0.statusKey == "published" && $0.visibilityKey == "public" }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Анонсы и feed")
                    .font(.title3.bold())
                    .accessibilityIdentifier("announcements.root")
                Text("Provider-agnostic event feed surface без push activation, `/api/v1/me/notifications` и background delivery.")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    AnnouncementErrorBanner(
                        message: errorMessage,
                        onDismiss: model.clearError
                    )
                    .accessibilityIdentifier("announcements.error")
                }

                if model.isLoading || model.isSubmitting {
                    ProgressView()
                        .accessibilityIdentifier("announcements.loading")
                }

                HStack(spacing: 12) {
                    Text("Анонсов: \(model.announcements.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("announcements.count")
                    Button("Обновить") {
                        model.refresh()
                    }
                    .buttonStyle(.bordered)
                    .disabled(selectedEventId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.isLoading || model.isSubmitting)
                    .accessibilityIdentifier("announcements.refresh")
                }

                if eligibleEvents.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Нужен опубликованный public event")
                            .font(.headline)
                        Text("Announcement feed доступен только для published public событий. Подготовьте событие на вкладке событий и вернитесь сюда.")
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .accessibilityIdentifier("announcements.event.empty")
                } else {
                    eventSelectionSection
                    publishSection

                    Divider()
                    announcementListSection
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onAppear(perform: syncDefaultEventSelection)
        .onChange(of: events.map(\.id)) { _, _ in
            syncDefaultEventSelection()
        }
        .onChange(of: model.selectedEventId) { _, _ in
            syncDefaultEventSelection()
        }
        .onChange(of: selectedEventId) { _, eventId in
            guard !eventId.isEmpty else { return }
            model.loadAnnouncements(eventId: eventId)
        }
    }

    private var eventSelectionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Событие для feed-а")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(eligibleEvents) { event in
                        Button(event.title) {
                            selectedEventId = event.id
                        }
                        .buttonStyle(
                            AnnouncementEventSelectionButtonStyle(
                                isSelected: selectedEventId == event.id
                            )
                        )
                        .accessibilityIdentifier("announcements.event.\(event.id)")
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var publishSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Опубликовать announcement")
                .font(.headline)
            Text(
                eligibleEvents.first(where: { $0.id == selectedEventId }).map {
                    "Feed будет опубликован в audience-visible историю события «\($0.title)». Публикация требует organizer/host access."
                } ?? "Сначала выберите published public event."
            )
                .foregroundColor(.secondary)
            TextField("Текст announcement-а", text: $draftMessage)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("announcements.message")
            Text("\(draftMessage.trimmingCharacters(in: .whitespacesAndNewlines).count)/1000")
                .font(.footnote)
                .foregroundColor(.secondary)
                .accessibilityIdentifier("announcements.counter")
            Button("Опубликовать") {
                model.createAnnouncement(
                    eventId: selectedEventId,
                    message: draftMessage
                )
            }
            .buttonStyle(.borderedProminent)
            .disabled(
                selectedEventId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    draftMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    model.isLoading ||
                    model.isSubmitting
            )
            .accessibilityIdentifier("announcements.publish")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var announcementListSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("История анонсов")
                .font(.headline)
            if model.announcements.isEmpty {
                Text("Пока нет анонсов для выбранного события")
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("announcements.empty")
            } else {
                ForEach(model.announcements) { announcement in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(announcement.message)
                        Text("\(authorRoleTitle(announcement.authorRoleKey)) · \(announcement.createdAtIso)")
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .accessibilityIdentifier("announcements.card.\(announcement.id)")
                }
            }
        }
    }

    private func syncDefaultEventSelection() {
        guard let firstEligibleEventId = eligibleEvents.first?.id else {
            selectedEventId = ""
            return
        }
        if let selectedByModel = model.selectedEventId,
           !selectedByModel.isEmpty,
           eligibleEvents.contains(where: { $0.id == selectedByModel }) {
            selectedEventId = selectedByModel
            return
        }
        if selectedEventId.isEmpty || !eligibleEvents.contains(where: { $0.id == selectedEventId }) {
            selectedEventId = firstEligibleEventId
        }
    }
}

private struct AnnouncementErrorBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct AnnouncementEventSelectionButtonStyle: ButtonStyle {
    let isSelected: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .foregroundColor(isSelected ? Color.white : Color.primary)
            .background(isSelected ? Color.accentColor : Color(.secondarySystemBackground))
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.accentColor.opacity(isSelected ? 0 : 0.4), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.8 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

private func authorRoleTitle(_ key: String) -> String {
    switch key {
    case "organizer":
        return "Организатор"
    case "host":
        return "Ведущий"
    case "system":
        return "Система"
    default:
        return key
    }
}
