import SwiftUI

/// SwiftUI-экран donation history и comedian payout surface внутри авторизованного tab shell.
struct DonationHubView: View {
    @ObservedObject var model: DonationHubModel

    @State private var selectedLegalTypeKey: String = "self_employed"
    @State private var beneficiaryRef: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Донаты и выплаты")
                    .font(.title3.bold())
                    .accessibilityIdentifier("donations.root")
                Text("Provider-agnostic surface для payout profile и истории донатов без checkout и выбора PSP")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    DonationsBanner(
                        message: errorMessage,
                        onDismiss: model.clearError
                    )
                    .accessibilityIdentifier("donations.error")
                }

                if model.isLoading || model.isSubmittingPayoutProfile {
                    ProgressView()
                        .accessibilityIdentifier("donations.loading")
                }

                HStack(spacing: 12) {
                    Text("Отправлено: \(model.sentDonations.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("donations.count.sent")
                    if model.hasComedianRole {
                        Text("Получено: \(model.receivedDonations.count)")
                            .foregroundColor(.secondary)
                            .accessibilityIdentifier("donations.count.received")
                    }
                    Button("Обновить") {
                        model.refresh()
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isLoading || model.isSubmittingPayoutProfile)
                    .accessibilityIdentifier("donations.refresh")
                }

                if model.hasComedianRole {
                    payoutProfileSection
                        .onAppear {
                            syncFormFromProfileIfNeeded()
                        }
                } else {
                    lockedPayoutSection
                }

                Divider()
                DonationListSection(
                    title: "Мои отправленные донаты",
                    donations: model.sentDonations,
                    emptyIdentifier: "donations.sent.empty"
                )

                if model.hasComedianRole {
                    Divider()
                    DonationListSection(
                        title: "Полученные донаты",
                        donations: model.receivedDonations,
                        emptyIdentifier: "donations.received.empty"
                    )
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onChange(of: model.payoutProfile?.updatedAtIso) { _, _ in
            syncFormFromProfileIfNeeded()
        }
    }

    private var payoutProfileSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Профиль выплат комика")
                .font(.headline)
            Text(
                model.payoutProfile.map {
                    "Статус: \(payoutVerificationTitle($0.verificationStatusKey)) · Провайдер: \($0.payoutProvider)"
                } ?? "Профиль пока не заполнен. Первый foundation slice использует manual settlement."
            )
            .accessibilityIdentifier("donations.payout.status")

            VStack(alignment: .leading, spacing: 8) {
                ForEach(DonationLegalTypeOption.allCases) { option in
                    Button(selectedLegalTypeKey == option.key ? "• \(option.title)" : option.title) {
                        selectedLegalTypeKey = option.key
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isSubmittingPayoutProfile)
                    .accessibilityIdentifier("donations.payout.legalType.\(option.key)")
                }
            }

            TextField("Beneficiary ref", text: $beneficiaryRef)
                .textFieldStyle(.roundedBorder)
                .disabled(model.isSubmittingPayoutProfile)
                .accessibilityIdentifier("donations.payout.beneficiary")

            Button("Сохранить payout profile") {
                model.savePayoutProfile(
                    legalTypeKey: selectedLegalTypeKey,
                    beneficiaryRef: beneficiaryRef
                )
            }
            .buttonStyle(.borderedProminent)
            .disabled(beneficiaryRef.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.isSubmittingPayoutProfile)
            .accessibilityIdentifier("donations.payout.save")

            if let payoutProfile = model.payoutProfile {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Актуальный payout profile")
                        .font(.headline)
                        .accessibilityIdentifier("donations.payout.card")
                    Text("Юртип: \(payoutLegalTypeTitle(payoutProfile.legalTypeKey))")
                    Text("Beneficiary ref: \(payoutProfile.beneficiaryRef)")
                    Text("Статус проверки: \(payoutVerificationTitle(payoutProfile.verificationStatusKey))")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var lockedPayoutSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Payout profile станет доступен после выдачи роли комика")
                .font(.headline)
                .accessibilityIdentifier("donations.payout.locked")
            Text("До этого здесь остается только audience-side история отправленных донатов.")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func syncFormFromProfileIfNeeded() {
        guard let payoutProfile = model.payoutProfile else { return }
        selectedLegalTypeKey = payoutProfile.legalTypeKey
        beneficiaryRef = payoutProfile.beneficiaryRef
    }
}

private struct DonationsBanner: View {
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
            Button("Скрыть") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct DonationListSection: View {
    let title: String
    let donations: [DonationItem]
    let emptyIdentifier: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            if donations.isEmpty {
                Text("Пока пусто")
                    .foregroundColor(.secondary)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .accessibilityIdentifier(emptyIdentifier)
            } else {
                ForEach(donations) { donation in
                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(donation.comedianDisplayName) · \(formatAmountMinor(donation.amountMinor, donation.currency))")
                            .font(.headline)
                        Text("Событие: \(donation.eventTitle)")
                        Text("Донатор: \(donation.donorDisplayName)")
                        Text("Статус: \(donationStatusTitle(donation.statusKey))")
                        if let message = donation.message {
                            Text("Сообщение: \(message)")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .accessibilityIdentifier("donations.card.\(donation.id)")
                }
            }
        }
    }
}

private enum DonationLegalTypeOption: String, CaseIterable, Identifiable {
    case individual
    case selfEmployed = "self_employed"
    case soleProprietor = "sole_proprietor"
    case company

    var id: String { rawValue }
    var key: String { rawValue }
    var title: String { payoutLegalTypeTitle(rawValue) }
}

private func payoutLegalTypeTitle(_ key: String) -> String {
    switch key {
    case "individual":
        return "Физлицо"
    case "self_employed":
        return "Самозанятый"
    case "sole_proprietor":
        return "ИП"
    case "company":
        return "Компания"
    default:
        return key
    }
}

private func payoutVerificationTitle(_ key: String) -> String {
    switch key {
    case "pending":
        return "На проверке"
    case "verified":
        return "Проверен"
    case "rejected":
        return "Отклонён"
    case "blocked":
        return "Заблокирован"
    default:
        return key
    }
}

private func donationStatusTitle(_ key: String) -> String {
    switch key {
    case "created":
        return "Создан"
    case "awaiting_payment":
        return "Ожидает оплату"
    case "paid":
        return "Оплачен"
    case "payout_pending":
        return "Ожидает выплату"
    case "paid_out":
        return "Выплачен"
    case "failed":
        return "Ошибка"
    case "reversed":
        return "Отменён"
    default:
        return key
    }
}

private func formatAmountMinor(_ amountMinor: Int, _ currency: String) -> String {
    let major = amountMinor / 100
    let minor = amountMinor % 100
    return "\(currency) \(major).\(String(format: "%02d", minor))"
}
