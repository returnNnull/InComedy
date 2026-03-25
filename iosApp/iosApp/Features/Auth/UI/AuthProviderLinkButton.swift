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

/// Preview-обертка провайдерной кнопки без macro-based `#Preview`.
private struct AuthProviderLinkButton_Previews: PreviewProvider {
    /// Возвращает preview-конфигурацию кнопки VK-входа.
    static var previews: some View {
        AuthProviderLinkButton(provider: .vk, isLoading: false, onTap: {})
    }
}
