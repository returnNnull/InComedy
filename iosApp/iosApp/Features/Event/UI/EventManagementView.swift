import SwiftUI

/// SwiftUI-экран organizer event management внутри авторизованного tab shell.
struct EventManagementView: View {
    /// Модель organizer event feature.
    @ObservedObject var model: EventScreenModel

    /// Organizer workspaces, доступные текущему пользователю.
    let workspaces: [MainWorkspaceItem]

    /// Выбранный workspace для формы события.
    @State private var selectedWorkspaceId: String = ""

    /// Выбранная площадка для формы события.
    @State private var selectedVenueId: String = ""

    /// Выбранный hall template.
    @State private var selectedTemplateId: String = ""

    /// Название события.
    @State private var eventTitle: String = ""

    /// Описание события.
    @State private var eventDescription: String = ""

    /// ISO timestamp начала события.
    @State private var eventStartsAt: String = "2026-04-01T19:00:00+03:00"

    /// ISO timestamp открытия дверей.
    @State private var eventDoorsOpenAt: String = "2026-04-01T18:30:00+03:00"

    /// ISO timestamp окончания.
    @State private var eventEndsAt: String = "2026-04-01T21:00:00+03:00"

    /// Валюта события.
    @State private var eventCurrency: String = "RUB"

    /// Visibility события.
    @State private var eventVisibilityKey: String = "public"

    /// Возвращает площадки выбранного workspace.
    private var filteredVenues: [EventVenueItem] {
        model.venues.filter { $0.workspaceId == selectedWorkspaceId }
    }

    /// Возвращает выбранную площадку или первую доступную.
    private var selectedVenue: EventVenueItem? {
        filteredVenues.first(where: { $0.id == selectedVenueId }) ?? filteredVenues.first
    }

    /// Возвращает выбранный hall template или первый доступный.
    private var selectedTemplate: EventTemplateItem? {
        selectedVenue?.hallTemplates.first(where: { $0.id == selectedTemplateId }) ?? selectedVenue?.hallTemplates.first
    }

    /// Отрисовывает organizer event surface с формой создания и списком событий.
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("События и snapshot схемы")
                    .font(.title3.bold())
                    .accessibilityIdentifier("event.root")
                Text("Organizer slice для create/list/publish и frozen EventHallSnapshot")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    EventErrorBanner(
                        message: errorMessage,
                        onDismiss: model.clearError
                    )
                }

                if model.isLoading || model.isSubmitting {
                    ProgressView()
                        .accessibilityIdentifier("event.loading")
                }

                HStack(spacing: 12) {
                    Text("Событий: \(model.events.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("event.count")
                    Button("Обновить") {
                        model.refresh()
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isLoading || model.isSubmitting)
                    .accessibilityIdentifier("event.refresh")
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Создать событие")
                        .font(.headline)
                    if workspaces.isEmpty {
                        Text("Сначала создайте organizer workspace, затем площадку и шаблон зала.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("event.workspace.empty")
                    } else {
                        EventSelectionStrip(
                            items: workspaces.map { EventSelectionStripItem(id: $0.id, title: $0.name) },
                            selectedKey: selectedWorkspaceId,
                            onSelect: { selectedWorkspaceId = $0 },
                            tagPrefix: "event.workspace."
                        )
                    }

                    if filteredVenues.isEmpty {
                        Text("В выбранном workspace пока нет площадок.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("event.venue.empty")
                    } else {
                        EventSelectionStrip(
                            items: filteredVenues.map { EventSelectionStripItem(id: $0.id, title: $0.name) },
                            selectedKey: selectedVenue?.id ?? "",
                            onSelect: { selectedVenueId = $0 },
                            tagPrefix: "event.venue."
                        )
                    }

                    if let selectedVenue {
                        if selectedVenue.hallTemplates.isEmpty {
                            Text("Для выбранной площадки еще нет шаблонов зала.")
                                .foregroundColor(.secondary)
                                .accessibilityIdentifier("event.template.empty")
                        } else {
                            EventSelectionStrip(
                                items: selectedVenue.hallTemplates.map {
                                    EventSelectionStripItem(id: $0.id, title: "\($0.name) · v\($0.version)")
                                },
                                selectedKey: selectedTemplate?.id ?? "",
                                onSelect: { selectedTemplateId = $0 },
                                tagPrefix: "event.template."
                            )
                        }
                    }

                    TextField("Название события", text: $eventTitle)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("event.form.title")
                    TextField("Описание", text: $eventDescription, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(2...4)
                        .accessibilityIdentifier("event.form.description")
                    TextField("Начало (ISO)", text: $eventStartsAt)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("event.form.startsAt")
                    TextField("Открытие дверей (ISO)", text: $eventDoorsOpenAt)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("event.form.doorsOpenAt")
                    TextField("Окончание (ISO)", text: $eventEndsAt)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("event.form.endsAt")
                    TextField("Валюта", text: $eventCurrency)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("event.form.currency")
                    EventSelectionStrip(
                        items: [
                            EventSelectionStripItem(id: "public", title: "Публичное"),
                            EventSelectionStripItem(id: "private", title: "Приватное")
                        ],
                        selectedKey: eventVisibilityKey,
                        onSelect: { eventVisibilityKey = $0 },
                        tagPrefix: "event.visibility."
                    )
                    Button("Сохранить событие") {
                        model.createEvent(
                            workspaceId: selectedWorkspaceId,
                            venueId: selectedVenue?.id ?? "",
                            hallTemplateId: selectedTemplate?.id ?? "",
                            title: eventTitle,
                            description: eventDescription,
                            startsAtIso: eventStartsAt,
                            doorsOpenAtIso: eventDoorsOpenAt,
                            endsAtIso: eventEndsAt,
                            currency: eventCurrency,
                            visibilityKey: eventVisibilityKey
                        )
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(
                        selectedWorkspaceId.isEmpty ||
                        selectedVenue == nil ||
                        selectedTemplate == nil ||
                        eventTitle.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                        model.isSubmitting
                    )
                    .accessibilityIdentifier("event.form.create")
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Список событий")
                        .font(.headline)
                    if model.events.isEmpty {
                        Text("Пока нет событий. Создайте первый draft и затем опубликуйте его.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("event.list.empty")
                    } else {
                        ForEach(model.events) { event in
                            EventCardView(
                                event: event,
                                isSubmitting: model.isSubmitting,
                                onPublish: { model.publishEvent(eventId: event.id) }
                            )
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .onAppear {
            syncWorkspaceSelection()
            syncVenueSelection()
            syncTemplateSelection()
        }
        .onChange(of: workspaces.map(\.id)) { _, _ in
            syncWorkspaceSelection()
        }
        .onChange(of: model.venues.map(\.id)) { _, _ in
            syncVenueSelection()
            syncTemplateSelection()
        }
        .onChange(of: selectedWorkspaceId) { _, _ in
            syncVenueSelection()
            syncTemplateSelection()
        }
        .onChange(of: selectedVenue?.id) { _, _ in
            syncTemplateSelection()
        }
    }

    /// Выравнивает выбранный workspace после изменений session state.
    private func syncWorkspaceSelection() {
        if selectedWorkspaceId.isEmpty || !workspaces.contains(where: { $0.id == selectedWorkspaceId }) {
            selectedWorkspaceId = workspaces.first?.id ?? ""
        }
    }

    /// Выравнивает выбранную площадку после изменений venue context.
    private func syncVenueSelection() {
        if selectedVenueId.isEmpty || !filteredVenues.contains(where: { $0.id == selectedVenueId }) {
            selectedVenueId = filteredVenues.first?.id ?? ""
        }
    }

    /// Выравнивает выбранный hall template после изменений площадки.
    private func syncTemplateSelection() {
        guard let selectedVenue else {
            selectedTemplateId = ""
            return
        }
        if selectedTemplateId.isEmpty || !selectedVenue.hallTemplates.contains(where: { $0.id == selectedTemplateId }) {
            selectedTemplateId = selectedVenue.hallTemplates.first?.id ?? ""
        }
    }
}

/// Баннер ошибки organizer event screen.
private struct EventErrorBanner: View {
    /// Сообщение безопасной ошибки.
    let message: String

    /// Колбэк скрытия ошибки.
    let onDismiss: () -> Void

    /// Отрисовывает ошибку и действие скрытия.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .foregroundColor(.red)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.red.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("event.error")
    }
}

/// Карточка organizer event в списке событий.
private struct EventCardView: View {
    /// Отображаемое organizer event.
    let event: EventItem

    /// Показывает активную create/publish мутацию.
    let isSubmitting: Bool

    /// Команда публикации draft-события.
    let onPublish: () -> Void

    /// Отрисовывает карточку события и publish action для draft.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(event.title)
                .font(.headline)
            Text("\(event.venueName) · \(event.sourceTemplateName)")
                .foregroundColor(.secondary)
            Text("Старт: \(event.startsAtIso)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            Text("Статус: \(eventStatusTitle(event.statusKey)) · Продажи: \(salesStatusTitle(event.salesStatusKey))")
                .font(.subheadline)
                .foregroundColor(.secondary)
            Text("Snapshot: \(event.layoutSummary)")
                .font(.subheadline)
            if event.statusKey == "draft" {
                Button("Опубликовать") {
                    onPublish()
                }
                .buttonStyle(.borderedProminent)
                .disabled(isSubmitting)
                .accessibilityIdentifier("event.publish.\(event.id)")
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// Полоса выбора для workspace/venue/template и visibility.
private struct EventSelectionStrip: View {
    /// Варианты выбора.
    let items: [EventSelectionStripItem]

    /// Текущий выбранный ключ.
    let selectedKey: String

    /// Колбэк выбора.
    let onSelect: (String) -> Void

    /// Префикс accessibility identifier для кнопок.
    let tagPrefix: String

    /// Отрисовывает адаптивную группу кнопок выбора.
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items) { item in
                    selectionButton(for: item)
                }
            }
        }
    }

    /// Возвращает кнопку выбора для одного варианта.
    ///
    /// - Parameter item: Элемент выбора.
    @ViewBuilder
    private func selectionButton(for item: EventSelectionStripItem) -> some View {
        if selectedKey == item.id {
            Button(item.title) {
                onSelect(item.id)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("\(tagPrefix)\(item.id)")
        } else {
            Button(item.title) {
                onSelect(item.id)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("\(tagPrefix)\(item.id)")
        }
    }
}

/// Вариант выбора внутри `EventSelectionStrip`.
private struct EventSelectionStripItem: Identifiable {
    /// Уникальный ключ варианта.
    let id: String

    /// Человекочитаемый заголовок варианта.
    let title: String
}

/// Преобразует event status wire-name в человекочитаемый заголовок.
private func eventStatusTitle(_ key: String) -> String {
    switch key {
    case "draft": return "Черновик"
    case "published": return "Опубликовано"
    case "canceled": return "Отменено"
    default: return key
    }
}

/// Преобразует sales status wire-name в человекочитаемый заголовок.
private func salesStatusTitle(_ key: String) -> String {
    switch key {
    case "closed": return "Закрыты"
    case "open": return "Открыты"
    case "paused": return "На паузе"
    case "sold_out": return "Sold out"
    default: return key
    }
}
