import SwiftUI

struct AuthGraphView: View {
    let onAuthorized: () -> Void

    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            AuthRootView(onAuthorized: onAuthorized)
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .authHome:
                        AuthRootView(onAuthorized: onAuthorized)
                    }
                }
        }
    }
}

#Preview {
    AuthGraphView(onAuthorized: {})
}
