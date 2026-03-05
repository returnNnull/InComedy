import Foundation
import Security
import Shared

final class AuthScreenModel: ObservableObject {
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isAuthorized: Bool = false
    @Published var statusText: String = "Выберите провайдера"
    @Published var pendingOpenURL: URL?

    private let bridge: AuthFeatureBridge
    private let accessTokenStore = AuthTokenKeychainStore(account: "auth.access_token")
    private let refreshTokenStore = AuthTokenKeychainStore(account: "auth.refresh_token")

    private var bindingHandle: NSObject?
    private let accessTokenKey = "auth.access_token"
    private let refreshTokenKey = "auth.refresh_token"

    init(bridge: AuthFeatureBridge? = nil) {
        self.bridge = bridge ?? AuthFeatureBridge(
            viewModel: InComedyKoin.shared.getAuthViewModel()
        )
        bind()
        restoreSessionIfPossible()
    }

    deinit {
        disposeBindingIfNeeded()
        bridge.dispose()
    }

    func onTap(provider: AuthProvider) {
        print("AUTH_FLOW stage=ios.start_auth.requested provider=\(provider.rawValue)")
        bridge.startAuth(providerKey: provider.rawValue)
    }

    func onOpenURLHandled() {
        pendingOpenURL = nil
    }

    func onIncomingCallback(url: URL) {
        print("AUTH_FLOW stage=ios.callback_url.received hasUrl=true")
        bridge.completeAuthFromCallbackUrl(callbackUrl: url.absoluteString)
    }

    private func bind() {
        setBinding(
            bridge.bind(
                onState: { [weak self] snapshot in
                    guard let self else { return }
                    Task { @MainActor in
                        self.isLoading = snapshot.isLoading
                        self.errorMessage = snapshot.errorMessage
                        self.isAuthorized = snapshot.isAuthorized

                        if snapshot.isAuthorized, let provider = snapshot.authorizedProviderKey {
                            self.statusText = "Успешная авторизация через \(provider.uppercased())"
                            if let accessToken = snapshot.authorizedAccessToken {
                                self.accessTokenStore.save(token: accessToken)
                            }
                            if let refreshToken = snapshot.authorizedRefreshToken, !refreshToken.isEmpty {
                                self.refreshTokenStore.save(token: refreshToken)
                            } else {
                                self.refreshTokenStore.deleteToken()
                            }
                        } else if let provider = snapshot.selectedProviderKey,
                                  let knownProvider = AuthProvider(rawValue: provider) {
                            self.statusText = knownProvider.openedStatus
                        } else {
                            self.statusText = "Выберите провайдера"
                        }
                    }
                },
                onOpenUrl: { [weak self] urlString in
                    guard let self, let url = URL(string: urlString) else { return }
                    print("AUTH_FLOW stage=ios.launch_url.received")
                    Task { @MainActor in
                        self.pendingOpenURL = url
                    }
                },
                onInvalidateSession: { [weak self] in
                    guard let self else { return }
                    self.deleteStoredToken()
                }
            )
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

    private func restoreSessionIfPossible() {
        guard let accessToken = loadStoredAccessToken() else {
            return
        }
        let refreshToken = loadStoredRefreshToken()
        print("AUTH_FLOW stage=ios.session.restore.requested")
        bridge.restoreSessionTokens(accessToken: accessToken, refreshToken: refreshToken)
    }

    private func loadStoredAccessToken() -> String? {
        if let secureToken = accessTokenStore.loadToken() {
            return secureToken
        }

        // One-time migration from legacy plain UserDefaults.
        guard let legacyToken = UserDefaults.standard.string(forKey: accessTokenKey), !legacyToken.isEmpty else {
            return nil
        }
        accessTokenStore.save(token: legacyToken)
        UserDefaults.standard.removeObject(forKey: accessTokenKey)
        print("AUTH_FLOW stage=ios.session.token.migrated_to_keychain")
        return legacyToken
    }

    private func loadStoredRefreshToken() -> String? {
        if let secureToken = refreshTokenStore.loadToken() {
            return secureToken
        }

        guard let legacyToken = UserDefaults.standard.string(forKey: refreshTokenKey), !legacyToken.isEmpty else {
            return nil
        }
        refreshTokenStore.save(token: legacyToken)
        UserDefaults.standard.removeObject(forKey: refreshTokenKey)
        return legacyToken
    }

    private func deleteStoredToken() {
        accessTokenStore.deleteToken()
        refreshTokenStore.deleteToken()
        UserDefaults.standard.removeObject(forKey: accessTokenKey)
        UserDefaults.standard.removeObject(forKey: refreshTokenKey)
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

    func save(token: String) {
        guard let data = token.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)

        var addQuery = query
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    func loadToken() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let token = String(data: data, encoding: .utf8),
              !token.isEmpty else {
            return nil
        }
        return token
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
