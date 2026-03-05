import Foundation
import Security
import Shared

final class MainSessionModel: ObservableObject {
    @Published var isAuthorized: Bool = false
    @Published var displayName: String = "Сессия активна"
    @Published var providerKey: String?

    private let bridge: SessionBridge
    private var bindingHandle: NSObject?
    private let tokenStore = AuthTokenKeychainStore()

    init(bridge: SessionBridge? = nil) {
        self.bridge = bridge ?? SessionBridge(
            viewModel: InComedyKoin.shared.getSessionViewModel()
        )
        bind()
    }

    deinit {
        disposeBindingIfNeeded()
        bridge.dispose()
    }

    func signOut() {
        tokenStore.deleteToken()
        bridge.signOut()
    }

    private func bind() {
        setBinding(
            bridge.observeState { [weak self] snapshot in
                guard let self else { return }
                Task { @MainActor in
                    self.isAuthorized = snapshot.isAuthorized
                    self.providerKey = snapshot.providerKey
                    self.displayName = snapshot.displayName ?? "Сессия активна"
                }
            }
        )
    }

    private func setBinding(_ handle: Any) {
        disposeBindingIfNeeded()
        bindingHandle = handle as? NSObject
    }

    private func disposeBindingIfNeeded() {
        guard let bindingHandle else { return }
        let disposeSelector = NSSelectorFromString("dispose")
        if bindingHandle.responds(to: disposeSelector) {
            _ = bindingHandle.perform(disposeSelector)
        }
        self.bindingHandle = nil
    }
}

private final class AuthTokenKeychainStore {
    private let service: String
    private let account: String

    init(
        service: String = Bundle.main.bundleIdentifier ?? "com.bam.incomedy",
        account: String = "auth.access_token"
    ) {
        self.service = service
        self.account = account
    }

    func deleteToken() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
