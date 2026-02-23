import SwiftUI

struct AuthGraphView: View {
    let onAuthorized: () -> Void

    var body: some View {
        NavigationStack {
            AuthRootView(onAuthorized: onAuthorized)
        }
    }
}

#Preview {
    AuthGraphView(onAuthorized: {})
}
