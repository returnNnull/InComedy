import Foundation
import Shared

@MainActor
final class AuthScreenModel: BridgeBackedObservableObject {
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?
    @Published var isAuthorized: Bool = false
    @Published var statusText: String = "Выберите провайдера"
    @Published var pendingOpenURL: URL?

    private let bridge: AuthFeatureBridge

    init(bridge: AuthFeatureBridge? = nil) {
        self.bridge = bridge ?? AuthScreenModel.makeDefaultBridge()
        super.init()
        bind()
    }

    deinit {
        bridge.dispose()
    }

    func onTap(provider: AuthProvider) {
        bridge.startAuth(providerKey: provider.rawValue)
    }

    func onOpenURLHandled() {
        pendingOpenURL = nil
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
                    Task { @MainActor in
                        self.pendingOpenURL = url
                    }
                }
            )
        )
    }

    private static func makeDefaultBridge() -> AuthFeatureBridge {
        AuthFeatureBridge(
            viewModel: InComedyKoin.shared.getAuthViewModel()
        )
    }
}
