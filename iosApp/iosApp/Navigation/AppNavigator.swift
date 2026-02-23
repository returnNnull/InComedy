import Foundation

@MainActor
final class AppNavigator: ObservableObject {
    @Published private(set) var activeGraph: AppGraph = .auth

    func showMain() {
        activeGraph = .main
    }

    func showAuth() {
        activeGraph = .auth
    }
}
