import SwiftUI

struct MainGraphView: View {
    let onSignOut: () -> Void
    @StateObject private var model = MainSessionModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Главная")
                    .font(.title3.bold())
                Text(model.displayName)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Button("Выйти") {
                    model.signOut()
                    onSignOut()
                }
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .onChange(of: model.isAuthorized) { isAuthorized in
            if !isAuthorized {
                onSignOut()
            }
        }
    }
}

#Preview {
    MainGraphView(onSignOut: {})
}
