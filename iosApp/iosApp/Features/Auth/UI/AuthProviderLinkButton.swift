import SwiftUI

struct AuthProviderLinkButton: View {
    let provider: AuthProvider
    let isLoading: Bool
    let onTap: () -> Void

    var body: some View {
        Button(provider.title) {
            onTap()
        }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading)
    }
}

#Preview {
    AuthProviderLinkButton(provider: .vk, isLoading: false, onTap: {})
}
