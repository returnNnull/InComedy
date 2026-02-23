import SwiftUI

struct ContentView: View {
    @State private var statusText: String = "Выберите провайдера"

    var body: some View {
        VStack(spacing: 16) {
            Text("Авторизация")
                .font(.title2.bold())

            Text(statusText)
                .font(.subheadline)
                .foregroundColor(.secondary)

            Link("Войти через VK", destination: URL(string: "https://id.vk.com/authorize")!)
                .buttonStyle(.borderedProminent)
                .simultaneousGesture(TapGesture().onEnded {
                    statusText = "Открыт VK OAuth"
                })

            Link("Войти через Telegram", destination: URL(string: "https://oauth.telegram.org/auth")!)
                .buttonStyle(.borderedProminent)
                .simultaneousGesture(TapGesture().onEnded {
                    statusText = "Открыт Telegram OAuth"
                })

            Link("Войти через Google", destination: URL(string: "https://accounts.google.com/o/oauth2/v2/auth")!)
                .buttonStyle(.borderedProminent)
                .simultaneousGesture(TapGesture().onEnded {
                    statusText = "Открыт Google OAuth"
                })
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
