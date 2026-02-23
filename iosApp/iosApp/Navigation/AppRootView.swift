import SwiftUI

struct AppRootView: View {
    @StateObject private var navigator = AppNavigator()

    var body: some View {
        switch navigator.activeGraph {
        case .auth:
            AuthGraphView(onAuthorized: navigator.showMain)
        case .main:
            MainGraphView(onSignOut: navigator.showAuth)
        }
    }
}

#Preview {
    AppRootView()
}
