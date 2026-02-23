import SwiftUI

struct MainGraphView: View {
    let onSignOut: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Main Graph Placeholder")
                    .font(.title3.bold())
                Text("Следующий шаг: заменить на реальные экраны post-auth.")
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Button("Выйти") {
                    onSignOut()
                }
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

#Preview {
    MainGraphView(onSignOut: {})
}
