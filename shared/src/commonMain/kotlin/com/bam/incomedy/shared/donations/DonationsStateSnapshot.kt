package com.bam.incomedy.shared.donations

/**
 * Export-friendly состояние donation overview и comedian payout surface для Swift-слоя.
 */
data class DonationsStateSnapshot(
    val payoutProfile: PayoutProfileSnapshot?,
    val sentDonations: List<DonationIntentSnapshot>,
    val receivedDonations: List<DonationIntentSnapshot>,
    val hasComedianRole: Boolean,
    val isLoading: Boolean,
    val isSubmittingPayoutProfile: Boolean,
    val errorMessage: String?,
)

/** Export-friendly payout profile snapshot. */
data class PayoutProfileSnapshot(
    val id: String,
    val userId: String,
    val payoutProvider: String,
    val legalTypeKey: String,
    val beneficiaryRef: String,
    val verificationStatusKey: String,
    val createdAtIso: String,
    val updatedAtIso: String,
    val statusUpdatedAtIso: String,
)

/** Export-friendly donation intent snapshot. */
data class DonationIntentSnapshot(
    val id: String,
    val eventId: String,
    val eventTitle: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val donorUserId: String,
    val donorDisplayName: String,
    val amountMinor: Int,
    val currency: String,
    val message: String?,
    val statusKey: String,
    val createdAtIso: String,
    val updatedAtIso: String,
)
