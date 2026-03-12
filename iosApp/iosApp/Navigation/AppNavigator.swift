import Foundation

/// Навигатор верхнего уровня между авторизованным и неавторизованным графом iOS-приложения.
@MainActor
final class AppNavigator: ObservableObject {
    /// Текущий активный граф приложения.
    @Published private(set) var activeGraph: AppGraph = .auth

    /// Создает навигатор с начальным графом.
    ///
    /// - Parameter initialGraph: Граф, который должен открыться первым.
    init(initialGraph: AppGraph = .auth) {
        activeGraph = initialGraph
    }

    /// Переключает приложение в авторизованный граф.
    func showMain() {
        activeGraph = .main
    }

    /// Возвращает приложение в граф авторизации.
    func showAuth() {
        activeGraph = .auth
    }
}
