import SwiftUI

struct AuthRootView: View {
    var onAuthorized: (() -> Void)? = nil

    @Environment(\.openURL) private var openURL
    @StateObject private var model = AuthScreenModel()
    @State private var login: String = ""
    @State private var password: String = ""
    @State private var isRegisterMode: Bool = false

    var body: some View {
        VStack(spacing: 16) {
            Text("Авторизация")
                .font(.title2.bold())

            Text(model.statusText)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Picker("Режим", selection: $isRegisterMode) {
                Text("Вход").tag(false)
                Text("Регистрация").tag(true)
            }
            .pickerStyle(.segmented)

            TextField("Логин", text: $login)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)

            SecureField("Пароль", text: $password)
                .textFieldStyle(.roundedBorder)

            Button(isRegisterMode ? "Создать аккаунт" : "Войти") {
                if isRegisterMode {
                    model.register(login: login, password: password)
                } else {
                    model.signIn(login: login, password: password)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.isLoading || login.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)

            Divider()

            AuthProviderLinkButton(provider: .vk, isLoading: model.isLoading) {
                model.startVkAuth()
            }

            if let errorMessage = model.errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .padding()
        .onChange(of: model.isAuthorized) { isAuthorized in
            guard isAuthorized else { return }
            onAuthorized?()
        }
        .onChange(of: model.pendingOpenURL) { pendingURL in
            guard let pendingURL else { return }
            openURL(pendingURL)
            model.onOpenURLHandled()
        }
        .onOpenURL { url in
            model.handleCallback(url: url)
        }
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
            guard let url = activity.webpageURL else { return }
            model.handleCallback(url: url)
        }
    }
}

#Preview {
    AuthRootView()
}
