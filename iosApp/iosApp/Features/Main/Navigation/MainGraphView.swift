import SwiftUI

/// Главный контейнер авторизованной части приложения с нижним меню.
struct MainGraphView: View {
    /// Колбэк возврата в неавторизованный граф после выхода.
    let onSignOut: () -> Void

    /// Модель экрана, поставляющая данные профиля, ролей и рабочих пространств.
    @StateObject private var model: MainSessionModel

    /// Имя нового рабочего пространства из формы на вкладке "Главная".
    @State private var workspaceName: String = ""

    /// Необязательный slug нового рабочего пространства.
    @State private var workspaceSlug: String = ""

    /// Создает контейнер главного экрана с обычной или тестовой моделью.
    ///
    /// - Parameters:
    ///   - onSignOut: Колбэк возврата в неавторизованный граф.
    ///   - fixture: Необязательная фикстура экрана для UI-тестов и превью.
    init(
        onSignOut: @escaping () -> Void,
        fixture: MainSessionFixture? = nil
    ) {
        self.onSignOut = onSignOut
        _model = StateObject(
            wrappedValue: fixture.map(MainSessionModel.init(fixture:)) ?? MainSessionModel()
        )
    }

    /// Отрисовывает tab shell авторизованной части приложения.
    var body: some View {
        TabView {
            NavigationStack {
                ScrollView {
                    MainHomeTab(
                        model: model,
                        workspaceName: $workspaceName,
                        workspaceSlug: $workspaceSlug
                    )
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .tabItem {
                Label("Главная", systemImage: "house")
            }
            .accessibilityIdentifier("main.tab.home")

            NavigationStack {
                ScrollView {
                    AccountTab(
                        model: model
                    )
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .tabItem {
                Label("Аккаунт", systemImage: "person.crop.circle")
            }
            .accessibilityIdentifier("main.tab.account")
        }
        .accessibilityIdentifier("main.root")
        .onChange(of: model.isAuthorized) { isAuthorized in
            if !isAuthorized {
                onSignOut()
            }
        }
    }
}

/// Вкладка сводки по рабочим пространствам.
private struct MainHomeTab: View {
    /// Общая модель главного экрана.
    @ObservedObject var model: MainSessionModel

    /// Имя создаваемого рабочего пространства.
    @Binding var workspaceName: String

    /// Необязательный slug создаваемого рабочего пространства.
    @Binding var workspaceSlug: String

    /// Отрисовывает домашнюю вкладку со сводкой и формой создания рабочего пространства.
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Главная")
                .font(.title3.bold())
                .accessibilityIdentifier("main.content.home")
            Text(model.displayName.isEmpty ? "Рады видеть снова" : "Рады видеть, \(model.displayName)")
                .foregroundColor(.secondary)

            if let errorMessage = model.errorMessage {
                ErrorBanner(
                    message: errorMessage,
                    onDismiss: model.clearError
                )
            }

            if model.isLoadingContext || model.isUpdatingRole || model.isCreatingWorkspace {
                ProgressView()
            }

            Text("Рабочих пространств: \(model.workspaces.count)")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .accessibilityIdentifier("main.home.workspaceCount")

            if model.workspaces.isEmpty {
                Text("Пока нет рабочих пространств")
                    .foregroundColor(.secondary)
            } else {
                ForEach(model.workspaces) { workspace in
                    WorkspaceCard(workspace: workspace)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Создать рабочее пространство")
                    .font(.headline)
                TextField("Название", text: $workspaceName)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier("main.workspace.name")
                TextField("Slug (необязательно)", text: $workspaceSlug)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityIdentifier("main.workspace.slug")
                Button("Создать рабочее пространство") {
                    model.createWorkspace(
                        name: workspaceName,
                        slug: workspaceSlug.isEmpty ? nil : workspaceSlug
                    )
                }
                .buttonStyle(.borderedProminent)
                .disabled(model.isCreatingWorkspace || workspaceName.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                .accessibilityIdentifier("main.workspace.create")
            }
        }
    }
}

/// Вкладка аккаунта с профилем, фото, ролями и действием выхода.
private struct AccountTab: View {
    /// Общая модель главного экрана.
    @ObservedObject var model: MainSessionModel

    /// Отрисовывает вкладку аккаунта с профилем, ролями и действием выхода.
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Аккаунт")
                .font(.title3.bold())
                .accessibilityIdentifier("main.content.account")

            AccountHeader(model: model)

            ProfileField(
                title: "Имя профиля",
                value: model.displayName.isEmpty ? "Не указано" : model.displayName
            )
            ProfileField(
                title: "Username",
                value: model.username ?? "Не указан"
            )
            ProfileField(
                title: "ID пользователя",
                value: model.userId ?? "Недоступен"
            )
            ProfileField(
                title: "Привязанные входы",
                value: model.linkedProviderKeys.map(providerTitle).joined(separator: ", ").ifEmpty("Не указаны")
            )

            VStack(alignment: .leading, spacing: 8) {
                Text("Смена ролей")
                    .font(.headline)

                if model.roles.isEmpty {
                    Text("Роли пока не назначены")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(model.roles, id: \.self) { role in
                        if model.activeRoleKey == role {
                            Button {
                                model.setActiveRole(role)
                            } label: {
                                HStack {
                                    Text(roleTitle(role))
                                    Spacer()
                                    Text("Активна")
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(BorderedProminentButtonStyle())
                            .disabled(model.isUpdatingRole)
                            .accessibilityIdentifier("main.account.role.\(role)")
                        } else {
                            Button {
                                model.setActiveRole(role)
                            } label: {
                                HStack {
                                    Text(roleTitle(role))
                                    Spacer()
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .buttonStyle(BorderedButtonStyle())
                            .disabled(model.isUpdatingRole)
                            .accessibilityIdentifier("main.account.role.\(role)")
                        }
                    }
                }
            }

            Button("Выйти") {
                model.signOut()
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("main.account.signOut")
        }
    }
}

/// Верхний блок аккаунта с фото и активной ролью.
private struct AccountHeader: View {
    /// Общая модель аккаунта.
    @ObservedObject var model: MainSessionModel

    /// Отрисовывает верхний блок аккаунта с аватаром и активной ролью.
    var body: some View {
        HStack(spacing: 16) {
            ProfileAvatar(
                photoURL: model.photoURL,
                displayName: model.displayName
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(model.displayName.isEmpty ? "Профиль" : model.displayName)
                    .font(.title2.bold())
                Text(model.activeRoleKey.map(roleTitle) ?? "Роль не выбрана")
                    .foregroundColor(.secondary)
                    .accessibilityIdentifier("main.account.activeRole")
            }
        }
    }
}

/// Аватар профиля, который показывает удаленное фото или текстовую заглушку.
private struct ProfileAvatar: View {
    /// URL фотографии профиля.
    let photoURL: String?

    /// Имя пользователя для построения заглушки.
    let displayName: String

    /// Отрисовывает аватар пользователя или текстовую заглушку.
    var body: some View {
        Group {
            if let photoURL, let url = URL(string: photoURL) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    default:
                        PlaceholderAvatar(letter: fallbackLetter)
                    }
                }
            } else {
                PlaceholderAvatar(letter: fallbackLetter)
            }
        }
        .frame(width: 88, height: 88)
        .clipShape(Circle())
        .accessibilityIdentifier("main.account.avatar")
    }

    /// Возвращает букву-заглушку для аватара.
    private var fallbackLetter: String {
        let firstCharacter = displayName.trimmingCharacters(in: .whitespacesAndNewlines).first
        return firstCharacter.map { String($0).uppercased() } ?? "?"
    }
}

/// Заглушка аватара, если фото профиля недоступно.
private struct PlaceholderAvatar: View {
    /// Символ, используемый в центре аватара.
    let letter: String

    /// Отрисовывает заглушку аватара.
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.accentColor.opacity(0.2))
            Text(letter)
                .font(.title.bold())
                .foregroundColor(.accentColor)
        }
    }
}

/// Карточка рабочего пространства на главной вкладке.
private struct WorkspaceCard: View {
    /// Данные рабочего пространства.
    let workspace: MainWorkspaceItem

    /// Отрисовывает карточку рабочего пространства.
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(workspace.name)
                .font(.headline)
            Text("\(workspace.slug) · \(permissionRoleTitle(workspace.permissionRole)) · \(workspaceStatusTitle(workspace.status))")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

/// Строка с подписью и значением поля профиля.
private struct ProfileField: View {
    /// Подпись поля профиля.
    let title: String

    /// Значение поля профиля.
    let value: String

    /// Отрисовывает строку профиля.
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
        }
    }
}

/// Баннер верхнего уровня для ошибок экрана.
private struct ErrorBanner: View {
    /// Текст ошибки.
    let message: String

    /// Колбэк закрытия ошибки.
    let onDismiss: () -> Void

    /// Отрисовывает баннер ошибки для главного экрана.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
                .foregroundStyle(.red)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
    }
}

/// Переводит код роли в человекочитаемую подпись.
private func roleTitle(_ role: String) -> String {
    switch role {
    case "audience":
        return "Зритель"
    case "comedian":
        return "Комик"
    case "organizer":
        return "Организатор"
    default:
        return role
    }
}

/// Переводит код способа входа в подпись для интерфейса.
private func providerTitle(_ provider: String) -> String {
    switch provider {
    case "password":
        return "Логин и пароль"
    case "phone":
        return "Телефон"
    case "telegram":
        return "Telegram"
    case "vk":
        return "VK"
    case "google":
        return "Google"
    case "apple":
        return "Apple"
    default:
        return provider
    }
}

/// Переводит код роли доступа к рабочему пространству в подпись для интерфейса.
private func permissionRoleTitle(_ role: String) -> String {
    switch role {
    case "owner":
        return "Владелец"
    case "manager":
        return "Менеджер"
    case "checker":
        return "Чекер"
    case "host":
        return "Ведущий"
    default:
        return role
    }
}

/// Переводит код статуса рабочего пространства в подпись для интерфейса.
private func workspaceStatusTitle(_ status: String) -> String {
    switch status {
    case "active":
        return "Активно"
    default:
        return status
    }
}

/// Возвращает запасное значение для пустой строки.
private extension String {
    /// Подставляет значение, если строка пуста.
    func ifEmpty(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }
}

#Preview {
    MainGraphView(onSignOut: {}, fixture: .uiTestMain)
}
