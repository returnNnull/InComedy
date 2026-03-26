import Foundation
import Shared

/// SwiftUI-модель donation history и comedian payout surface.
final class DonationHubModel: ObservableObject {
    @Published var payoutProfile: PayoutProfileItem?
    @Published var sentDonations: [DonationItem] = []
    @Published var receivedDonations: [DonationItem] = []
    @Published var hasComedianRole: Bool = false
    @Published var isLoading: Bool = false
    @Published var isSubmittingPayoutProfile: Bool = false
    @Published var errorMessage: String?

    private let bridge: DonationsBridge?
    private var bindingHandle: NSObject?

    init(bridge: DonationsBridge? = nil) {
        self.bridge = bridge ?? DonationsBridge(
            viewModel: InComedyKoin.shared.getDonationsViewModel()
        )
        bind()
    }

    init(fixture: DonationHubFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    func refresh() {
        bridge?.refresh()
    }

    func savePayoutProfile(legalTypeKey: String, beneficiaryRef: String) {
        let normalizedBeneficiaryRef = beneficiaryRef.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.savePayoutProfile(
                legalTypeKey: legalTypeKey,
                beneficiaryRef: normalizedBeneficiaryRef
            )
            return
        }

        guard hasComedianRole else {
            errorMessage = "Payout profile доступен только роли комика"
            return
        }
        guard normalizedBeneficiaryRef.count >= 3 else {
            errorMessage = "Укажите beneficiary ref длиной от 3 символов"
            return
        }

        payoutProfile = PayoutProfileItem(
            id: payoutProfile?.id ?? "profile-local",
            userId: payoutProfile?.userId ?? "comedian-1",
            payoutProvider: "manual_settlement",
            legalTypeKey: legalTypeKey,
            beneficiaryRef: normalizedBeneficiaryRef,
            verificationStatusKey: "pending",
            createdAtIso: payoutProfile?.createdAtIso ?? "2026-03-25T19:00:00+03:00",
            updatedAtIso: "2026-03-25T22:00:00+03:00",
            statusUpdatedAtIso: "2026-03-25T22:00:00+03:00"
        )
        errorMessage = nil
    }

    func clearError() {
        if let bridge {
            bridge.clearError()
        } else {
            errorMessage = nil
        }
    }

    private func bind() {
        guard let bridge else { return }
        setBinding(
            bridge.observeState { [weak self] snapshot in
                guard let self else { return }
                Task { @MainActor in
                    self.apply(snapshot: snapshot)
                }
            }
        )
    }

    @MainActor
    private func apply(snapshot: DonationsStateSnapshot) {
        payoutProfile = snapshot.payoutProfile.map(PayoutProfileItem.init(snapshot:))
        sentDonations = snapshot.sentDonations.map(DonationItem.init(snapshot:))
        receivedDonations = snapshot.receivedDonations.map(DonationItem.init(snapshot:))
        hasComedianRole = snapshot.hasComedianRole
        isLoading = snapshot.isLoading
        isSubmittingPayoutProfile = snapshot.isSubmittingPayoutProfile
        errorMessage = snapshot.errorMessage
    }

    private func apply(fixture: DonationHubFixture) {
        payoutProfile = fixture.payoutProfile
        sentDonations = fixture.sentDonations
        receivedDonations = fixture.receivedDonations
        hasComedianRole = fixture.hasComedianRole
        isLoading = fixture.isLoading
        isSubmittingPayoutProfile = fixture.isSubmittingPayoutProfile
        errorMessage = fixture.errorMessage
    }

    private func setBinding(_ handle: Any) {
        disposeBindingIfNeeded()
        bindingHandle = handle as? NSObject
    }

    private func disposeBindingIfNeeded() {
        guard let bindingHandle else { return }
        let disposeSelector = NSSelectorFromString("dispose")
        if bindingHandle.responds(to: disposeSelector) {
            _ = bindingHandle.perform(disposeSelector)
        }
        self.bindingHandle = nil
    }
}

struct DonationHubFixture {
    let payoutProfile: PayoutProfileItem?
    let sentDonations: [DonationItem]
    let receivedDonations: [DonationItem]
    let hasComedianRole: Bool
    let isLoading: Bool
    let isSubmittingPayoutProfile: Bool
    let errorMessage: String?

    static func preview() -> DonationHubFixture {
        DonationHubFixture(
            payoutProfile: PayoutProfileItem(
                id: "profile-1",
                userId: "comedian-1",
                payoutProvider: "manual_settlement",
                legalTypeKey: "self_employed",
                beneficiaryRef: "+79990000000",
                verificationStatusKey: "pending",
                createdAtIso: "2026-03-25T19:00:00+03:00",
                updatedAtIso: "2026-03-25T19:30:00+03:00",
                statusUpdatedAtIso: "2026-03-25T19:30:00+03:00"
            ),
            sentDonations: [
                DonationItem(
                    id: "donation-1",
                    eventId: "event-1",
                    eventTitle: "Late Night Standup",
                    comedianUserId: "comedian-1",
                    comedianDisplayName: "Иван Смехов",
                    donorUserId: "user-1",
                    donorDisplayName: "Тестовый Пользователь",
                    amountMinor: 2500,
                    currency: "RUB",
                    message: "Спасибо за сет",
                    statusKey: "created",
                    createdAtIso: "2026-03-25T20:00:00+03:00",
                    updatedAtIso: "2026-03-25T20:10:00+03:00"
                )
            ],
            receivedDonations: [
                DonationItem(
                    id: "donation-2",
                    eventId: "event-2",
                    eventTitle: "Friday Roast",
                    comedianUserId: "comedian-1",
                    comedianDisplayName: "Иван Смехов",
                    donorUserId: "user-2",
                    donorDisplayName: "Анна Сцена",
                    amountMinor: 5000,
                    currency: "RUB",
                    message: "Еще!",
                    statusKey: "paid",
                    createdAtIso: "2026-03-25T21:00:00+03:00",
                    updatedAtIso: "2026-03-25T21:05:00+03:00"
                )
            ],
            hasComedianRole: true,
            isLoading: false,
            isSubmittingPayoutProfile: false,
            errorMessage: nil
        )
    }
}

struct PayoutProfileItem {
    let id: String
    let userId: String
    let payoutProvider: String
    let legalTypeKey: String
    let beneficiaryRef: String
    let verificationStatusKey: String
    let createdAtIso: String
    let updatedAtIso: String
    let statusUpdatedAtIso: String

    init(snapshot: PayoutProfileSnapshot) {
        id = snapshot.id
        userId = snapshot.userId
        payoutProvider = snapshot.payoutProvider
        legalTypeKey = snapshot.legalTypeKey
        beneficiaryRef = snapshot.beneficiaryRef
        verificationStatusKey = snapshot.verificationStatusKey
        createdAtIso = snapshot.createdAtIso
        updatedAtIso = snapshot.updatedAtIso
        statusUpdatedAtIso = snapshot.statusUpdatedAtIso
    }

    init(
        id: String,
        userId: String,
        payoutProvider: String,
        legalTypeKey: String,
        beneficiaryRef: String,
        verificationStatusKey: String,
        createdAtIso: String,
        updatedAtIso: String,
        statusUpdatedAtIso: String
    ) {
        self.id = id
        self.userId = userId
        self.payoutProvider = payoutProvider
        self.legalTypeKey = legalTypeKey
        self.beneficiaryRef = beneficiaryRef
        self.verificationStatusKey = verificationStatusKey
        self.createdAtIso = createdAtIso
        self.updatedAtIso = updatedAtIso
        self.statusUpdatedAtIso = statusUpdatedAtIso
    }
}

struct DonationItem: Identifiable {
    let id: String
    let eventId: String
    let eventTitle: String
    let comedianUserId: String
    let comedianDisplayName: String
    let donorUserId: String
    let donorDisplayName: String
    let amountMinor: Int
    let currency: String
    let message: String?
    let statusKey: String
    let createdAtIso: String
    let updatedAtIso: String

    init(snapshot: DonationIntentSnapshot) {
        id = snapshot.id
        eventId = snapshot.eventId
        eventTitle = snapshot.eventTitle
        comedianUserId = snapshot.comedianUserId
        comedianDisplayName = snapshot.comedianDisplayName
        donorUserId = snapshot.donorUserId
        donorDisplayName = snapshot.donorDisplayName
        amountMinor = Int(snapshot.amountMinor)
        currency = snapshot.currency
        message = snapshot.message
        statusKey = snapshot.statusKey
        createdAtIso = snapshot.createdAtIso
        updatedAtIso = snapshot.updatedAtIso
    }

    init(
        id: String,
        eventId: String,
        eventTitle: String,
        comedianUserId: String,
        comedianDisplayName: String,
        donorUserId: String,
        donorDisplayName: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        statusKey: String,
        createdAtIso: String,
        updatedAtIso: String
    ) {
        self.id = id
        self.eventId = eventId
        self.eventTitle = eventTitle
        self.comedianUserId = comedianUserId
        self.comedianDisplayName = comedianDisplayName
        self.donorUserId = donorUserId
        self.donorDisplayName = donorDisplayName
        self.amountMinor = amountMinor
        self.currency = currency
        self.message = message
        self.statusKey = statusKey
        self.createdAtIso = createdAtIso
        self.updatedAtIso = updatedAtIso
    }
}
