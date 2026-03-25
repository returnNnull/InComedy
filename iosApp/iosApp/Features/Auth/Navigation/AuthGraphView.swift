import SwiftUI

struct AuthGraphView: View {
    let onAuthorized: () -> Void

    var body: some View {
        NavigationStack {
            AuthRootView(onAuthorized: onAuthorized)
        }
    }
}

/// Preview-обертка auth graph для Xcode canvas без macro-based `#Preview`.
private struct AuthGraphView_Previews: PreviewProvider {
    /// Возвращает preview-конфигурацию auth navigation stack.
    static var previews: some View {
        AuthGraphView(onAuthorized: {})
    }
}
