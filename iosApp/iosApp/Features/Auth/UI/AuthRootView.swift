import SwiftUI

struct AuthRootView: View {
    var onAuthorized: (() -> Void)? = nil

    @Environment(\.openURL) private var openURL
    @StateObject private var model = AuthScreenModel()

    var body: some View {
        VStack(spacing: 16) {
            Text("Авторизация")
                .font(.title2.bold())

            Text(model.statusText)
                .font(.subheadline)
                .foregroundColor(.secondary)

            if let errorMessage = model.errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundColor(.red)
            }

            ForEach(AuthProvider.allCases) { provider in
                AuthProviderLinkButton(provider: provider, isLoading: model.isLoading
                ) {
                    model.onTap(provider: provider)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding()
        .onChange(of: model.pendingOpenURL) { url in
            guard let url else { return }
            openURL(url)
            model.onOpenURLHandled()
        }
        .onChange(of: model.isAuthorized) { isAuthorized in
            guard isAuthorized else { return }
            onAuthorized?()
        }
    }
}

#Preview {
    AuthRootView()
}
