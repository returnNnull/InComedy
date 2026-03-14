import Foundation

enum AuthProvider: String, CaseIterable, Identifiable {
    case password
    case vk

    var id: String { rawValue }

    var title: String {
        switch self {
        case .password: return "Войти по логину и паролю"
        case .vk: return "Войти через VK"
        }
    }

    var openedStatus: String {
        switch self {
        case .password: return "Открыт вход по логину и паролю"
        case .vk: return "Открыт VK OAuth"
        }
    }
}
