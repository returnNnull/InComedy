import SwiftUI

/// Корневой контейнер приложения, который переключает auth и main графы.
struct AppRootView: View {
    /// Показывает, что приложение запущено в режиме iOS UI-теста главного экрана.
    private let usesUITestMainFixture: Bool

    /// Навигатор верхнего уровня между auth и main графами.
    @StateObject private var navigator: AppNavigator

    /// Создает корневой контейнер с учетом специальных аргументов запуска тестов.
    init() {
        let usesUITestMainFixture = ProcessInfo.processInfo.arguments.contains("--ui-test-main")
        self.usesUITestMainFixture = usesUITestMainFixture
        _navigator = StateObject(
            wrappedValue: AppNavigator(
                initialGraph: usesUITestMainFixture ? .main : .auth
            )
        )
    }

    /// Отрисовывает текущий верхнеуровневый граф приложения.
    var body: some View {
        switch navigator.activeGraph {
        case .auth:
            AuthGraphView(onAuthorized: navigator.showMain)
        case .main:
            MainGraphView(
                onSignOut: navigator.showAuth,
                fixture: usesUITestMainFixture ? .uiTestMain : nil
            )
        }
    }
}

/// Preview-обертка корневого графа для Xcode canvas без macro-based `#Preview`.
private struct AppRootView_Previews: PreviewProvider {
    /// Возвращает базовую preview-конфигурацию корневого контейнера.
    static var previews: some View {
        AppRootView()
    }
}
