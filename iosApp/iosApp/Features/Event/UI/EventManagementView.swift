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

    /// Название события для create form.
    @State private var eventTitle: String = ""

    /// Описание события для create form.
    @State private var eventDescription: String = ""

    /// ISO timestamp начала события для create form.
    @State private var eventStartsAt: String = "2026-04-01T19:00:00+03:00"

    /// ISO timestamp открытия дверей для create form.
    @State private var eventDoorsOpenAt: String = "2026-04-01T18:30:00+03:00"

    /// ISO timestamp окончания для create form.
    @State private var eventEndsAt: String = "2026-04-01T21:00:00+03:00"

    /// Валюта события для create form.
    @State private var eventCurrency: String = "RUB"

    /// Visibility события для create form.
    @State private var eventVisibilityKey: String = "public"

    /// Выбранное событие для override editor-а.
    @State private var selectedEditableEventId: String = ""

    /// Название события для update form.
    @State private var editorTitle: String = ""

    /// Описание события для update form.
    @State private var editorDescription: String = ""

    /// ISO timestamp начала для update form.
    @State private var editorStartsAt: String = ""

    /// ISO timestamp открытия дверей для update form.
    @State private var editorDoorsOpenAt: String = ""

    /// ISO timestamp окончания для update form.
    @State private var editorEndsAt: String = ""

    /// Валюта для update form.
    @State private var editorCurrency: String = "RUB"

    /// Visibility для update form.
    @State private var editorVisibilityKey: String = "public"

    /// Текст event-local price zones.
    @State private var editorPriceZones: String = ""

    /// Текст pricing assignments.
    @State private var editorPricingAssignments: String = ""

    /// Текст availability overrides.
    @State private var editorAvailabilityOverrides: String = ""

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

    /// Возвращает выбранное событие для editor-а или первое доступное.
    private var selectedEditableEvent: EventItem? {
        model.events.first(where: { $0.id == selectedEditableEventId }) ?? model.events.first
    }

    /// Отрисовывает organizer event surface с create form, списком и override editor-ом.
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("События и snapshot схемы")
                    .font(.title3.bold())
                    .accessibilityIdentifier("event.root")
                Text("Organizer slice для create/list/publish, sales controls и event-local pricing/availability overrides")
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
                                onEdit: { loadEventIntoEditor(event) },
                                onPublish: { model.publishEvent(eventId: event.id) },
                                onOpenSales: { model.openEventSales(eventId: event.id) },
                                onPauseSales: { model.pauseEventSales(eventId: event.id) },
                                onCancel: { model.cancelEvent(eventId: event.id) }
                            )
                        }
                    }
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Редактор override-ов события")
                        .font(.headline)
                    if let selectedEditableEvent {
                        Text("Площадка: \(selectedEditableEvent.venueName) · snapshot: \(selectedEditableEvent.sourceTemplateName)")
                            .font(.subheadline.weight(.semibold))
                        Text("Доступные target refs: \(selectedEditableEvent.targetHintText)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("Текущий override state: \(selectedEditableEvent.overrideSummaryText)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        if !selectedEditableEvent.isEditable {
                            Text("Отмененное событие доступно только для чтения. Для правок нужен новый draft.")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        TextField("Название события", text: $editorTitle)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("event.update.title")
                        TextField("Описание", text: $editorDescription, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("event.update.description")
                        TextField("Начало (ISO)", text: $editorStartsAt)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("event.update.startsAt")
                        TextField("Открытие дверей (ISO)", text: $editorDoorsOpenAt)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("event.update.doorsOpenAt")
                        TextField("Окончание (ISO)", text: $editorEndsAt)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("event.update.endsAt")
                        TextField("Валюта", text: $editorCurrency)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("event.update.currency")
                        EventSelectionStrip(
                            items: [
                                EventSelectionStripItem(id: "public", title: "Публичное"),
                                EventSelectionStripItem(id: "private", title: "Приватное")
                            ],
                            selectedKey: editorVisibilityKey,
                            onSelect: { editorVisibilityKey = $0 },
                            tagPrefix: "event.update.visibility."
                        )
                        TextField(
                            "Event price zones `id|name|priceMinor|currency|salesStartAt|salesEndAt|sourceTemplatePriceZoneId`",
                            text: $editorPriceZones,
                            axis: .vertical
                        )
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...6)
                        .accessibilityIdentifier("event.update.priceZones")
                        TextField(
                            "Pricing assignments `targetType|targetRef|eventPriceZoneId`",
                            text: $editorPricingAssignments,
                            axis: .vertical
                        )
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...6)
                        .accessibilityIdentifier("event.update.pricingAssignments")
                        TextField(
                            "Availability overrides `targetType|targetRef|availabilityStatus`",
                            text: $editorAvailabilityOverrides,
                            axis: .vertical
                        )
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...6)
                        .accessibilityIdentifier("event.update.availabilityOverrides")
                        Button("Сохранить override-ы") {
                            model.updateEvent(
                                eventId: selectedEditableEvent.id,
                                title: editorTitle,
                                description: editorDescription,
                                startsAtIso: editorStartsAt,
                                doorsOpenAtIso: editorDoorsOpenAt,
                                endsAtIso: editorEndsAt,
                                currency: editorCurrency,
                                visibilityKey: editorVisibilityKey,
                                priceZonesText: editorPriceZones,
                                pricingAssignmentsText: editorPricingAssignments,
                                availabilityOverridesText: editorAvailabilityOverrides
                            )
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(
                            editorTitle.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                            !selectedEditableEvent.isEditable ||
                            model.isSubmitting
                        )
                        .accessibilityIdentifier("event.update.save")
                    } else {
                        Text("Выберите или создайте событие, чтобы настроить event-local override-ы.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("event.editor.empty")
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
            syncEditorSelection()
        }
        .onChange(of: workspaces.map(\.id)) { _, _ in
            syncWorkspaceSelection()
        }
        .onChange(of: model.venues.map(\.id)) { _, _ in
            syncVenueSelection()
            syncTemplateSelection()
        }
        .onChange(of: model.events.map(\.id)) { _, _ in
            syncEditorSelection()
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

    /// Выравнивает выбранное событие для override editor-а.
    private func syncEditorSelection() {
        if selectedEditableEventId.isEmpty || !model.events.contains(where: { $0.id == selectedEditableEventId }) {
            if let firstEvent = model.events.first {
                loadEventIntoEditor(firstEvent)
            } else {
                selectedEditableEventId = ""
                editorTitle = ""
                editorDescription = ""
                editorStartsAt = ""
                editorDoorsOpenAt = ""
                editorEndsAt = ""
                editorCurrency = "RUB"
                editorVisibilityKey = "public"
                editorPriceZones = ""
                editorPricingAssignments = ""
                editorAvailabilityOverrides = ""
            }
        }
    }

    /// Загружает выбранное событие в text-based override editor.
    ///
    /// - Parameter event: Событие, которое нужно редактировать.
    private func loadEventIntoEditor(_ event: EventItem) {
        selectedEditableEventId = event.id
        editorTitle = event.title
        editorDescription = event.description ?? ""
        editorStartsAt = event.startsAtIso
        editorDoorsOpenAt = event.doorsOpenAtIso ?? ""
        editorEndsAt = event.endsAtIso ?? ""
        editorCurrency = event.currency
        editorVisibilityKey = event.visibilityKey
        editorPriceZones = event.priceZonesText
        editorPricingAssignments = event.pricingAssignmentsText
        editorAvailabilityOverrides = event.availabilityOverridesText
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

    /// Показывает активную create/publish/update мутацию.
    let isSubmitting: Bool

    /// Команда загрузки события в editor.
    let onEdit: () -> Void

    /// Команда публикации draft-события.
    let onPublish: () -> Void

    /// Команда открытия или возобновления продаж.
    let onOpenSales: () -> Void

    /// Команда паузы активных продаж.
    let onPauseSales: () -> Void

    /// Команда отмены события.
    let onCancel: () -> Void

    /// Отрисовывает карточку события, edit action и lifecycle controls.
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
            Text(event.overrideSummaryText)
                .font(.subheadline)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Button("Редактировать") {
                        onEdit()
                    }
                    .buttonStyle(.bordered)
                    .disabled(isSubmitting || !event.isEditable)
                    .accessibilityIdentifier("event.edit.\(event.id)")
                    if event.statusKey == "draft" {
                        Button("Опубликовать") {
                            onPublish()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isSubmitting)
                        .accessibilityIdentifier("event.publish.\(event.id)")
                    }
                    if event.canOpenSales {
                        Button(event.salesStatusKey == "paused" ? "Возобновить продажи" : "Открыть продажи") {
                            onOpenSales()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isSubmitting)
                        .accessibilityIdentifier("event.sales.open.\(event.id)")
                    }
                }
                HStack(spacing: 8) {
                    if event.canPauseSales {
                        Button("Пауза продаж") {
                            onPauseSales()
                        }
                        .buttonStyle(.bordered)
                        .disabled(isSubmitting)
                        .accessibilityIdentifier("event.sales.pause.\(event.id)")
                    }
                    if event.canCancel {
                        Button("Отменить") {
                            onCancel()
                        }
                        .buttonStyle(.bordered)
                        .disabled(isSubmitting)
                        .accessibilityIdentifier("event.cancel.\(event.id)")
                    }
                }
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
