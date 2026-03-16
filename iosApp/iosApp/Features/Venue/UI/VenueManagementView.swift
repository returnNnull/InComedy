import SwiftUI

/// SwiftUI-экран organizer venue management внутри авторизованного tab shell.
struct VenueManagementView: View {
    /// Модель organizer venue feature.
    @ObservedObject var model: VenueScreenModel

    /// Organizer workspaces, доступные текущему пользователю.
    let workspaces: [MainWorkspaceItem]

    /// Выбранный workspace для создания площадки.
    @State private var selectedWorkspaceId: String = ""

    /// Имя создаваемой площадки.
    @State private var venueName: String = ""

    /// Город создаваемой площадки.
    @State private var venueCity: String = ""

    /// Адрес создаваемой площадки.
    @State private var venueAddress: String = ""

    /// IANA timezone создаваемой площадки.
    @State private var venueTimezone: String = "Europe/Moscow"

    /// Вместимость площадки в текстовом виде.
    @State private var venueCapacity: String = "120"

    /// Описание площадки.
    @State private var venueDescription: String = ""

    /// Контакты площадки в формате `label|value`.
    @State private var venueContacts: String = "Telegram|@venue"

    /// Выбранная площадка для hall template builder.
    @State private var selectedVenueId: String = ""

    /// Текущий редактируемый hall template.
    @State private var selectedTemplateId: String?

    /// Название редактируемого шаблона.
    @State private var templateName: String = ""

    /// Статус редактируемого шаблона.
    @State private var templateStatusKey: String = "draft"

    /// Название сцены.
    @State private var templateStageLabel: String = ""

    /// Поле ценовых зон builder-а.
    @State private var templatePriceZones: String = ""

    /// Поле standing/sector зон builder-а.
    @State private var templateZones: String = ""

    /// Поле рядов builder-а.
    @State private var templateRows: String = "row-a|A|10|"

    /// Поле столов builder-а.
    @State private var templateTables: String = ""

    /// Поле служебных зон builder-а.
    @State private var templateServiceAreas: String = ""

    /// Поле blocked seat refs builder-а.
    @State private var templateBlockedSeats: String = ""

    /// Возвращает выбранную площадку или первую доступную.
    private var selectedVenue: VenueItem? {
        model.venues.first(where: { $0.id == selectedVenueId }) ?? model.venues.first
    }

    /// Отрисовывает organizer venue surface с формой площадки и builder-секцией шаблонов.
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Площадки и схемы зала")
                    .font(.title3.bold())
                    .accessibilityIdentifier("venue.root")
                Text("Organizer slice для площадок, hall templates и базового 2D builder v1")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    VenueErrorBanner(
                        message: errorMessage,
                        onDismiss: model.clearError
                    )
                }

                if model.isLoading || model.isSubmitting {
                    ProgressView()
                        .accessibilityIdentifier("venue.loading")
                }

                HStack(spacing: 12) {
                    Text("Площадок: \(model.venues.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("venue.count")
                    Button("Обновить") {
                        model.refresh()
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isLoading || model.isSubmitting)
                    .accessibilityIdentifier("venue.refresh")
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Создать площадку")
                        .font(.headline)
                    if workspaces.isEmpty {
                        Text("Сначала создайте organizer workspace, затем можно будет завести площадку.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("venue.workspace.empty")
                    } else {
                        SelectionStrip(
                            items: workspaces.map { SelectionStripItem(id: $0.id, title: $0.name) },
                            selectedKey: selectedWorkspaceId,
                            onSelect: { selectedWorkspaceId = $0 },
                            tagPrefix: "venue.workspace."
                        )
                    }
                    TextField("Название площадки", text: $venueName)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("venue.form.name")
                    TextField("Город", text: $venueCity)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("venue.form.city")
                    TextField("Адрес", text: $venueAddress)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("venue.form.address")
                    TextField("Timezone", text: $venueTimezone)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("venue.form.timezone")
                    TextField("Вместимость", text: $venueCapacity)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("venue.form.capacity")
                    TextField("Описание", text: $venueDescription, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(2...4)
                        .accessibilityIdentifier("venue.form.description")
                    TextField("Контакты `label|value`", text: $venueContacts, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(2...4)
                        .accessibilityIdentifier("venue.form.contacts")
                    Button("Сохранить площадку") {
                        model.createVenue(
                            workspaceId: selectedWorkspaceId,
                            name: venueName,
                            city: venueCity,
                            address: venueAddress,
                            timezone: venueTimezone,
                            capacityText: venueCapacity,
                            description: venueDescription,
                            contactsText: venueContacts
                        )
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(
                        selectedWorkspaceId.isEmpty ||
                        venueName.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                        venueCity.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        venueAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        venueTimezone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        venueCapacity.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                        model.isSubmitting
                    )
                    .accessibilityIdentifier("venue.form.create")
                }

                Divider()

                if model.venues.isEmpty {
                    Text("Пока нет ни одной площадки. Сохраните первую площадку и затем соберите шаблон зала.")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("venue.list.empty")
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Выбрать площадку для builder")
                            .font(.headline)
                        SelectionStrip(
                            items: model.venues.map { SelectionStripItem(id: $0.id, title: $0.name) },
                            selectedKey: selectedVenue?.id ?? "",
                            onSelect: { selectedVenueId = $0 },
                            tagPrefix: "venue.selector."
                        )
                        if let selectedVenue {
                            VenueCardView(
                                venue: selectedVenue,
                                onEditTemplate: loadTemplate,
                                onCloneTemplate: { template in
                                    model.cloneHallTemplate(
                                        templateId: template.id,
                                        clonedName: "\(template.name) копия"
                                    )
                                }
                            )
                        }
                    }
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Конструктор шаблона зала")
                        .font(.headline)
                    if let selectedVenue {
                        Text("Площадка: \(selectedVenue.name)")
                            .font(.subheadline.weight(.semibold))
                        HStack(spacing: 8) {
                            Button("Новый шаблон") {
                                resetTemplateEditor()
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("venue.template.reset")
                            if selectedTemplateId != nil {
                                Button("Клонировать") {
                                    model.cloneHallTemplate(
                                        templateId: selectedTemplateId ?? "",
                                        clonedName: templateName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                            ? nil
                                            : "\(templateName) копия"
                                    )
                                }
                                .buttonStyle(.bordered)
                                .disabled(model.isSubmitting)
                                .accessibilityIdentifier("venue.template.clone.selected")
                            }
                        }
                        TextField("Название шаблона", text: $templateName)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("venue.template.name")
                        SelectionStrip(
                            items: [
                                SelectionStripItem(id: "draft", title: "Черновик"),
                                SelectionStripItem(id: "published", title: "Опубликован"),
                            ],
                            selectedKey: templateStatusKey,
                            onSelect: { templateStatusKey = $0 },
                            tagPrefix: "venue.template.status."
                        )
                        TextField("Сцена", text: $templateStageLabel)
                            .textFieldStyle(.roundedBorder)
                            .accessibilityIdentifier("venue.template.stage")
                        TextField("Ценовые зоны `id|name|defaultPriceMinor`", text: $templatePriceZones, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.priceZones")
                        TextField("Standing / sector зоны `id|name|capacity|priceZoneId|kind`", text: $templateZones, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.zones")
                        TextField("Ряды `rowId|rowLabel|seatCount|priceZoneId`", text: $templateRows, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.rows")
                        TextField("Столы `tableId|label|seatCount|priceZoneId`", text: $templateTables, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.tables")
                        TextField("Служебные зоны `areaId|name|kind`", text: $templateServiceAreas, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.serviceAreas")
                        TextField("Blocked seats", text: $templateBlockedSeats, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...4)
                            .accessibilityIdentifier("venue.template.blockedSeats")
                        Button(selectedTemplateId == nil ? "Создать шаблон" : "Сохранить изменения") {
                            model.saveHallTemplate(
                                venueId: selectedVenue.id,
                                templateId: selectedTemplateId,
                                name: templateName,
                                statusKey: templateStatusKey,
                                stageLabel: templateStageLabel,
                                priceZonesText: templatePriceZones,
                                zonesText: templateZones,
                                rowsText: templateRows,
                                tablesText: templateTables,
                                serviceAreasText: templateServiceAreas,
                                blockedSeatRefsText: templateBlockedSeats
                            )
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(
                            templateName.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ||
                            model.isSubmitting
                        )
                        .accessibilityIdentifier("venue.template.save")
                    } else {
                        Text("Выберите площадку, чтобы создать или отредактировать hall template.")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("venue.template.empty")
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .onAppear {
            syncWorkspaceSelection()
            syncVenueSelection()
        }
        .onChange(of: workspaces.map(\.id)) { _, _ in
            syncWorkspaceSelection()
        }
        .onChange(of: model.venues.map(\.id)) { _, _ in
            syncVenueSelection()
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

    /// Выравнивает выбранную площадку после refresh venue state.
    private func syncVenueSelection() {
        if selectedVenueId.isEmpty || !model.venues.contains(where: { $0.id == selectedVenueId }) {
            selectedVenueId = model.venues.first?.id ?? ""
        }
    }

    /// Сбрасывает template editor, если текущий template больше не принадлежит выбранной площадке.
    private func syncTemplateSelection() {
        guard let selectedVenue else {
            resetTemplateEditor()
            return
        }
        if let selectedTemplateId,
           selectedVenue.hallTemplates.contains(where: { $0.id == selectedTemplateId }) {
            return
        }
        resetTemplateEditor()
    }

    /// Подгружает выбранный шаблон в локальные поля editor-а.
    ///
    /// - Parameter template: Шаблон, который нужно отредактировать.
    private func loadTemplate(_ template: VenueTemplateItem) {
        selectedTemplateId = template.id
        templateName = template.name
        templateStatusKey = template.statusKey
        templateStageLabel = template.stageLabel
        templatePriceZones = template.priceZonesText
        templateZones = template.zonesText
        templateRows = template.rowsText
        templateTables = template.tablesText
        templateServiceAreas = template.serviceAreasText
        templateBlockedSeats = template.blockedSeatRefsText
    }

    /// Сбрасывает локальный builder form в режим создания нового шаблона.
    private func resetTemplateEditor() {
        selectedTemplateId = nil
        templateName = ""
        templateStatusKey = "draft"
        templateStageLabel = ""
        templatePriceZones = ""
        templateZones = ""
        templateRows = ""
        templateTables = ""
        templateServiceAreas = ""
        templateBlockedSeats = ""
    }
}

/// Ошибочный баннер organizer venue surface.
private struct VenueErrorBanner: View {
    /// Безопасный текст ошибки.
    let message: String

    /// Колбэк скрытия ошибки.
    let onDismiss: () -> Void

    /// Отрисовывает баннер ошибки и кнопку скрытия.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .foregroundColor(.red)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.red.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("venue.error")
    }
}

/// Карточка площадки с summary и действиями над вложенными hall templates.
private struct VenueCardView: View {
    /// Площадка organizer-а.
    let venue: VenueItem

    /// Колбэк перехода в режим редактирования шаблона.
    let onEditTemplate: (VenueTemplateItem) -> Void

    /// Колбэк клонирования существующего шаблона.
    let onCloneTemplate: (VenueTemplateItem) -> Void

    /// Отрисовывает площадку и список ее hall templates.
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(venue.name)
                .font(.headline)
            Text("\(venue.city) · \(venue.address)")
                .foregroundColor(.secondary)
            Text("Вместимость: \(venue.capacity) · \(venue.timezone)")
                .font(.subheadline)
                .foregroundColor(.secondary)
            if let description = venue.description, !description.isEmpty {
                Text(description)
                    .font(.subheadline)
            }
            if venue.hallTemplates.isEmpty {
                Text("Шаблонов зала пока нет")
                    .foregroundColor(.secondary)
            } else {
                ForEach(venue.hallTemplates) { template in
                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(template.name) · v\(template.version)")
                            .font(.subheadline.weight(.semibold))
                        Text(template.statusKey)
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(template.summaryText.isEmpty ? "Пустой builder layout" : template.summaryText)
                            .font(.caption)
                        HStack(spacing: 8) {
                            Button("Редактировать") {
                                onEditTemplate(template)
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("venue.template.edit.\(template.id)")
                            Button("Клон") {
                                onCloneTemplate(template)
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("venue.template.clone.\(template.id)")
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.secondary.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.secondary.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("venue.card.\(venue.id)")
    }
}

/// Горизонтальная лента выбора значения через compact bordered buttons.
private struct SelectionStrip: View {
    /// Набор элементов для выбора.
    let items: [SelectionStripItem]

    /// Текущее выбранное значение.
    let selectedKey: String

    /// Колбэк выбора нового значения.
    let onSelect: (String) -> Void

    /// Префикс accessibility id для каждой кнопки.
    let tagPrefix: String

    /// Отрисовывает горизонтальный набор selectable chips.
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items) { item in
                    selectionButton(for: item)
                }
            }
            .padding(.vertical, 2)
        }
    }

    /// Возвращает кнопку selector-а с явным стилем без смешивания разных `ButtonStyle` в одном выражении.
    ///
    /// - Parameter item: Элемент горизонтального selector-а.
    @ViewBuilder
    private func selectionButton(for item: SelectionStripItem) -> some View {
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

/// Элемент горизонтального selector-а для SwiftUI-вкладки venue management.
private struct SelectionStripItem: Identifiable {
    /// Stable key выбранного значения.
    let id: String

    /// Отображаемая подпись кнопки.
    let title: String
}
