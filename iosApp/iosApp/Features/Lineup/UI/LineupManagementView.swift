import SwiftUI

/// SwiftUI-экран comedian applications и organizer lineup внутри авторизованного tab shell.
struct LineupManagementView: View {
    /// Модель lineup feature.
    @ObservedObject var model: LineupScreenModel

    /// Фаза текущей scene для stop/start lifecycle realtime feed-а.
    @Environment(\.scenePhase) private var scenePhase

    /// Organizer events, доступные для выбора context-а.
    let events: [EventItem]

    /// Выбранное organizer event для review/reorder context.
    @State private var selectedOrganizerEventId: String = ""

    /// Event id формы подачи заявки.
    @State private var submitEventId: String = ""

    /// Заметка к comedian application.
    @State private var submitNote: String = ""

    /// Хранит факт, что lineup tab сейчас видим пользователю.
    @State private var isVisible: Bool = false

    /// Отрисовывает lineup surface с organizer review/reorder и submit form.
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Лайнап и заявки")
                    .font(.title3.bold())
                    .accessibilityIdentifier("lineup.root")
                Text("Organizer review/reorder и простой comedian submit form поверх общего KMP foundation")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    LineupErrorBanner(
                        message: errorMessage,
                        onDismiss: model.clearError
                    )
                }

                if model.isLoading || model.isSubmitting {
                    ProgressView()
                        .accessibilityIdentifier("lineup.loading")
                }

                HStack(spacing: 12) {
                    Text("Заявок: \(model.applications.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("lineup.count.applications")
                    Text("Лайнап: \(model.lineup.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("lineup.count.entries")
                }

                Divider()

                OrganizerContextSection(
                    events: events,
                    selectedOrganizerEventId: $selectedOrganizerEventId,
                    isBusy: model.isLoading || model.isSubmitting,
                    onLoad: {
                        model.loadOrganizerContext(eventId: selectedOrganizerEventId)
                    }
                )

                Divider()

                SubmitApplicationSection(
                    submitEventId: $submitEventId,
                    submitNote: $submitNote,
                    isBusy: model.isSubmitting,
                    onSubmit: {
                        model.submitApplication(
                            eventId: submitEventId,
                            note: submitNote
                        )
                    }
                )

                Divider()

                ApplicationsSection(
                    applications: model.applications,
                    eventTitleById: Dictionary(uniqueKeysWithValues: events.map { ($0.id, $0.title) }),
                    isBusy: model.isSubmitting,
                    onUpdateStatus: { applicationId, eventId, statusKey in
                        model.updateApplicationStatus(
                            eventId: eventId,
                            applicationId: applicationId,
                            statusKey: statusKey
                        )
                    }
                )

                Divider()

                LiveStageSummarySection(
                    currentPerformerName: model.currentPerformer?.comedianDisplayName,
                    nextUpPerformerName: model.nextUpPerformer?.comedianDisplayName
                )

                Divider()

                LineupSection(
                    entries: model.lineup,
                    eventTitleById: Dictionary(uniqueKeysWithValues: events.map { ($0.id, $0.title) }),
                    selectedEventId: model.selectedEventId ?? selectedOrganizerEventId,
                    isBusy: model.isSubmitting,
                    onMoveEntry: { entryId, delta in
                        let eventId = model.selectedEventId ?? selectedOrganizerEventId
                        guard !eventId.isEmpty else { return }
                        model.moveLineupEntry(
                            eventId: eventId,
                            entryId: entryId,
                            delta: delta
                        )
                    },
                    onUpdateStatus: { entryId, statusKey in
                        let eventId = model.selectedEventId ?? selectedOrganizerEventId
                        guard !eventId.isEmpty else { return }
                        model.updateLineupEntryStatus(
                            eventId: eventId,
                            entryId: entryId,
                            statusKey: statusKey
                        )
                    }
                )
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onAppear(perform: syncDefaultEventSelection)
        .onAppear {
            isVisible = true
            syncLiveUpdatesActivation()
        }
        .onDisappear {
            isVisible = false
            syncLiveUpdatesActivation()
        }
        .onChange(of: events.map(\.id)) { _, _ in
            syncDefaultEventSelection()
        }
        .onChange(of: scenePhase) { _, _ in
            syncLiveUpdatesActivation()
        }
    }

    /// Синхронизирует дефолтный organizer event с доступным event list.
    private func syncDefaultEventSelection() {
        guard let firstEventId = events.first?.id else { return }
        if selectedOrganizerEventId.isEmpty {
            selectedOrganizerEventId = firstEventId
        }
        if submitEventId.isEmpty {
            submitEventId = firstEventId
        }
    }

    /// Включает realtime feed только пока вкладка видима и приложение активно.
    private func syncLiveUpdatesActivation() {
        model.setLiveUpdatesActive(isVisible && scenePhase == .active)
    }
}

/// Сводка текущего и следующего комика для live-stage surface.
private struct LiveStageSummarySection: View {
    /// Имя текущего комика на сцене.
    let currentPerformerName: String?

    /// Имя ближайшего следующего комика.
    let nextUpPerformerName: String?

    /// Отрисовывает audience/organizer summary на основе текущего lineup state.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Live stage")
                .font(.headline)
            Text(currentPerformerName.map { "Сейчас на сцене: \($0)" } ?? "Сейчас на сцене: еще не выбран")
                .accessibilityIdentifier("lineup.live.current")
            Text(nextUpPerformerName.map { "Следующий: \($0)" } ?? "Следующий: еще не выбран")
                .foregroundColor(.secondary)
                .accessibilityIdentifier("lineup.live.next")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("lineup.live")
    }
}

/// Секция выбора organizer event и загрузки context-а.
private struct OrganizerContextSection: View {
    /// Список доступных organizer events.
    let events: [EventItem]

    /// Выбранный organizer event.
    @Binding var selectedOrganizerEventId: String

    /// Признак активной загрузки или мутации.
    let isBusy: Bool

    /// Колбэк явной загрузки organizer context.
    let onLoad: () -> Void

    /// Отрисовывает выбор organizer event и кнопку загрузки context-а.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Organizer context")
                .font(.headline)
            if events.isEmpty {
                Text("Organizer events пока не загружены. Используйте submit form ниже, если нужен только comedian apply flow.")
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("lineup.organizer.empty")
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(events) { event in
                            Button(event.title) {
                                selectedOrganizerEventId = event.id
                            }
                            .buttonStyle(
                                EventSelectionButtonStyle(
                                    isSelected: selectedOrganizerEventId == event.id
                                )
                            )
                            .accessibilityIdentifier("lineup.event.\(event.id)")
                        }
                    }
                }
                Button("Загрузить заявки и лайнап") {
                    onLoad()
                }
                .buttonStyle(.borderedProminent)
                .disabled(selectedOrganizerEventId.isEmpty || isBusy)
                .accessibilityIdentifier("lineup.organizer.load")
            }
        }
    }
}

/// Секция comedian submit form.
private struct SubmitApplicationSection: View {
    /// Event id формы подачи заявки.
    @Binding var submitEventId: String

    /// Заметка к заявке.
    @Binding var submitNote: String

    /// Признак активной мутации.
    let isBusy: Bool

    /// Колбэк отправки формы.
    let onSubmit: () -> Void

    /// Отрисовывает submit form по явному `eventId`.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Подать заявку")
                .font(.headline)
            Text("Форма принимает явный `eventId`, пока public discovery slice еще не добавлен в мобильный клиент")
                .foregroundColor(.secondary)
            TextField("Event ID", text: $submitEventId)
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("lineup.submit.eventId")
            TextField("Заметка к заявке", text: $submitNote, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(2...4)
                .accessibilityIdentifier("lineup.submit.note")
            Button("Отправить заявку") {
                onSubmit()
            }
            .buttonStyle(.borderedProminent)
            .disabled(submitEventId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isBusy)
            .accessibilityIdentifier("lineup.submit.button")
        }
    }
}

/// Секция organizer review списка заявок.
private struct ApplicationsSection: View {
    /// Список текущих заявок.
    let applications: [ComedianApplicationItem]

    /// Карта названий событий по id.
    let eventTitleById: [String: String]

    /// Признак активной мутации.
    let isBusy: Bool

    /// Колбэк смены organizer review-статуса.
    let onUpdateStatus: (String, String, String) -> Void

    /// Отрисовывает список заявок или пустое состояние.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Заявки комиков")
                .font(.headline)
            if applications.isEmpty {
                Text("После загрузки organizer context здесь появятся comedian applications.")
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("lineup.applications.empty")
            } else {
                ForEach(applications) { application in
                    ApplicationCard(
                        application: application,
                        eventTitle: eventTitleById[application.eventId] ?? application.eventId,
                        isBusy: isBusy,
                        onUpdateStatus: onUpdateStatus
                    )
                }
            }
        }
    }
}

/// Карточка одной comedian application.
private struct ApplicationCard: View {
    /// Данные заявки.
    let application: ComedianApplicationItem

    /// Название связанного события.
    let eventTitle: String

    /// Признак активной мутации.
    let isBusy: Bool

    /// Колбэк смены статуса.
    let onUpdateStatus: (String, String, String) -> Void

    /// Отрисовывает данные заявки и organizer review actions.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(application.comedianDisplayName)
                .font(.headline)
            Text("Событие: \(eventTitle)")
                .foregroundColor(.secondary)
            Text("Статус: \(applicationStatusTitle(application.statusKey))")
                .accessibilityIdentifier("lineup.application.status.\(application.id)")
            if let note = application.note, !note.isEmpty {
                Text("Заметка: \(note)")
                    .foregroundColor(.secondary)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(reviewActions, id: \.statusKey) { action in
                        Button(action.title) {
                            onUpdateStatus(
                                application.id,
                                application.eventId,
                                action.statusKey
                            )
                        }
                        .buttonStyle(
                            EventSelectionButtonStyle(
                                isSelected: application.statusKey == action.statusKey
                            )
                        )
                        .disabled(isBusy)
                        .accessibilityIdentifier("lineup.application.action.\(application.id).\(action.statusKey)")
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        // Сохраняем дочерние accessibilityIdentifier для XCUITest, не схлопывая всю карточку в один элемент.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("lineup.application.card.\(application.id)")
    }
}

/// Секция organizer lineup и reorder controls.
private struct LineupSection: View {
    /// Упорядоченные lineup entries.
    let entries: [LineupEntryItem]

    /// Карта названий событий по id.
    let eventTitleById: [String: String]

    /// Выбранное событие organizer context-а.
    let selectedEventId: String

    /// Признак активной мутации.
    let isBusy: Bool

    /// Колбэк смещения записи вверх или вниз.
    let onMoveEntry: (String, Int) -> Void

    /// Колбэк смены live-stage статуса записи.
    let onUpdateStatus: (String, String) -> Void

    /// Отрисовывает список lineup entries или пустое состояние.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Текущий лайнап")
                .font(.headline)
            if entries.isEmpty {
                Text(
                    selectedEventId.isEmpty
                        ? "Сначала выберите organizer event и загрузите контекст."
                        : "Approved заявки пока не материализовали lineup для этого события."
                )
                .foregroundColor(.secondary)
                .accessibilityIdentifier("lineup.entries.empty")
            } else {
                ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                    LineupEntryCard(
                        entry: entry,
                        eventTitle: eventTitleById[entry.eventId] ?? entry.eventId,
                        canMoveUp: index > 0,
                        canMoveDown: index < entries.count - 1,
                        isBusy: isBusy,
                        onMoveUp: { onMoveEntry(entry.id, -1) },
                        onMoveDown: { onMoveEntry(entry.id, +1) },
                        onUpdateStatus: { statusKey in
                            onUpdateStatus(entry.id, statusKey)
                        }
                    )
                }
            }
        }
    }
}

/// Карточка одного lineup entry с reorder controls.
private struct LineupEntryCard: View {
    /// Данные entry.
    let entry: LineupEntryItem

    /// Название связанного события.
    let eventTitle: String

    /// Признак доступности смещения вверх.
    let canMoveUp: Bool

    /// Признак доступности смещения вниз.
    let canMoveDown: Bool

    /// Признак активной мутации.
    let isBusy: Bool

    /// Колбэк смещения вверх.
    let onMoveUp: () -> Void

    /// Колбэк смещения вниз.
    let onMoveDown: () -> Void

    /// Колбэк смены live-stage статуса.
    let onUpdateStatus: (String) -> Void

    /// Отрисовывает карточку lineup entry.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(entry.orderIndex). \(entry.comedianDisplayName)")
                .font(.headline)
                .accessibilityIdentifier("lineup.entry.order.\(entry.id)")
            Text("Событие: \(eventTitle)")
                .foregroundColor(.secondary)
            Text("Статус: \(lineupStatusTitle(entry.statusKey))")
                .accessibilityIdentifier("lineup.entry.status.\(entry.id)")
            HStack(spacing: 8) {
                Button("Выше") {
                    onMoveUp()
                }
                .buttonStyle(.bordered)
                .disabled(!canMoveUp || isBusy)
                .accessibilityIdentifier("lineup.entry.moveUp.\(entry.id)")
                Button("Ниже") {
                    onMoveDown()
                }
                .buttonStyle(.bordered)
                .disabled(!canMoveDown || isBusy)
                .accessibilityIdentifier("lineup.entry.moveDown.\(entry.id)")
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(liveStageActions, id: \.statusKey) { action in
                        Button(action.title) {
                            onUpdateStatus(action.statusKey)
                        }
                        .buttonStyle(
                            EventSelectionButtonStyle(
                                isSelected: entry.statusKey == action.statusKey
                            )
                        )
                        .disabled(isBusy)
                        .accessibilityIdentifier("lineup.entry.statusAction.\(entry.id).\(action.statusKey)")
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        // Сохраняем отдельные reorder/status идентификаторы внутри карточки для XCUITest.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("lineup.entry.card.\(entry.id)")
    }
}

/// Баннер верхнего уровня для lineup-ошибок.
private struct LineupErrorBanner: View {
    /// Текст ошибки.
    let message: String

    /// Колбэк закрытия ошибки.
    let onDismiss: () -> Void

    /// Отрисовывает баннер ошибки.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .foregroundStyle(.red)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .accessibilityIdentifier("lineup.error")
    }
}

/// Стиль компактной selection-button для event/status strips.
private struct EventSelectionButtonStyle: ButtonStyle {
    /// Показывает, что кнопка сейчас выбрана.
    let isSelected: Bool

    /// Отрисовывает выбранную или нейтральную кнопку.
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color.accentColor.opacity(0.15) : Color.clear)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// Описание доступного organizer review action.
private struct ReviewAction {
    /// Wire-ключ целевого статуса.
    let statusKey: String

    /// Подпись action-кнопки.
    let title: String
}

/// Список review-actions текущего MVP slice-а.
private let reviewActions: [ReviewAction] = [
    ReviewAction(statusKey: "shortlisted", title: "Шортлист"),
    ReviewAction(statusKey: "approved", title: "Approve"),
    ReviewAction(statusKey: "waitlisted", title: "Waitlist"),
    ReviewAction(statusKey: "rejected", title: "Reject")
]

/// Описание доступного live-stage action.
private struct LiveStageAction {
    /// Wire-ключ целевого live-stage статуса.
    let statusKey: String

    /// Подпись action-кнопки.
    let title: String
}

/// Список live-stage action-кнопок текущего organizer MVP slice-а.
private let liveStageActions: [LiveStageAction] = [
    LiveStageAction(statusKey: "draft", title: "В draft"),
    LiveStageAction(statusKey: "up_next", title: "Следующий"),
    LiveStageAction(statusKey: "on_stage", title: "На сцене"),
    LiveStageAction(statusKey: "done", title: "Выступил"),
    LiveStageAction(statusKey: "delayed", title: "Задержка"),
    LiveStageAction(statusKey: "dropped", title: "Снят")
]

/// Возвращает подпись review-статуса заявки.
private func applicationStatusTitle(_ statusKey: String) -> String {
    switch statusKey {
    case "submitted":
        return "На ревью"
    case "shortlisted":
        return "Шортлист"
    case "approved":
        return "Одобрена"
    case "waitlisted":
        return "Лист ожидания"
    case "rejected":
        return "Отклонена"
    case "withdrawn":
        return "Снята"
    default:
        return statusKey
    }
}

/// Возвращает подпись текущего MVP-статуса lineup entry.
private func lineupStatusTitle(_ statusKey: String) -> String {
    switch statusKey {
    case "draft":
        return "Draft"
    case "up_next":
        return "Следующий"
    case "on_stage":
        return "На сцене"
    case "done":
        return "Выступил"
    case "delayed":
        return "Задержан"
    case "dropped":
        return "Снят"
    default:
        return statusKey
    }
}
