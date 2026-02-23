import Foundation

enum AuthProvider: String, CaseIterable, Identifiable {
    case vk
    case telegram
    case google

    var id: String { rawValue }

    var title: String {
        switch self {
        case .vk: return "Войти через VK"
        case .telegram: return "Войти через Telegram"
        case .google: return "Войти через Google"
        }
    }

    var openedStatus: String {
        switch self {
        case .vk: return "Открыт VK OAuth"
        case .telegram: return "Открыт Telegram OAuth"
        case .google: return "Открыт Google OAuth"
        }
    }
}
