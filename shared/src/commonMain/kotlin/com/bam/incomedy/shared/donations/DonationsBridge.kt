package com.bam.incomedy.shared.donations

import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile
import com.bam.incomedy.feature.donations.DonationsState
import com.bam.incomedy.feature.donations.DonationsViewModel
import com.bam.incomedy.shared.bridge.BaseFeatureBridge
import com.bam.incomedy.shared.di.InComedyKoin

/**
 * Bridge над общей donations feature model для Swift-слоя.
 */
class DonationsBridge(
    private val viewModel: DonationsViewModel = InComedyKoin.getDonationsViewModel(),
) : BaseFeatureBridge() {
    fun currentState(): DonationsStateSnapshot = viewModel.state.value.toSnapshot()

    fun observeState(onState: (DonationsStateSnapshot) -> Unit) = observeState(
        stateFlow = viewModel.state,
        mapper = { it.toSnapshot() },
        onState = onState,
    )

    fun refresh() {
        viewModel.loadOverview()
    }

    fun savePayoutProfile(
        legalTypeKey: String,
        beneficiaryRef: String,
    ) {
        val legalType = PayoutLegalType.fromWireName(legalTypeKey) ?: return
        viewModel.savePayoutProfile(
            legalType = legalType,
            beneficiaryRef = beneficiaryRef,
        )
    }

    fun clearError() {
        viewModel.clearError()
    }
}

private fun DonationsState.toSnapshot(): DonationsStateSnapshot {
    return DonationsStateSnapshot(
        payoutProfile = payoutProfile?.toSnapshot(),
        sentDonations = sentDonations.map(DonationIntent::toSnapshot),
        receivedDonations = receivedDonations.map(DonationIntent::toSnapshot),
        hasComedianRole = hasComedianRole,
        isLoading = isLoading,
        isSubmittingPayoutProfile = isSubmittingPayoutProfile,
        errorMessage = errorMessage,
    )
}

private fun PayoutProfile.toSnapshot(): PayoutProfileSnapshot {
    return PayoutProfileSnapshot(
        id = id,
        userId = userId,
        payoutProvider = payoutProvider,
        legalTypeKey = legalType.wireName,
        beneficiaryRef = beneficiaryRef,
        verificationStatusKey = verificationStatus.wireName,
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
        statusUpdatedAtIso = statusUpdatedAtIso,
    )
}

private fun DonationIntent.toSnapshot(): DonationIntentSnapshot {
    return DonationIntentSnapshot(
        id = id,
        eventId = eventId,
        eventTitle = eventTitle,
        comedianUserId = comedianUserId,
        comedianDisplayName = comedianDisplayName,
        donorUserId = donorUserId,
        donorDisplayName = donorDisplayName,
        amountMinor = amountMinor,
        currency = currency,
        message = message,
        statusKey = status.wireName,
        createdAtIso = createdAtIso,
        updatedAtIso = updatedAtIso,
    )
}
