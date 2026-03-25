package com.bam.incomedy.data.donations.backend

import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.DonationService
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile

/**
 * Backend-адаптер provider-agnostic donations/payout service contract-а.
 */
class BackendDonationService(
    private val donationBackendApi: DonationBackendApi,
) : DonationService {
    override suspend fun getMyPayoutProfile(
        accessToken: String,
    ): Result<PayoutProfile?> {
        return donationBackendApi.getMyPayoutProfile(accessToken = accessToken)
    }

    override suspend fun upsertMyPayoutProfile(
        accessToken: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ): Result<PayoutProfile> {
        return donationBackendApi.upsertMyPayoutProfile(
            accessToken = accessToken,
            legalType = legalType,
            beneficiaryRef = beneficiaryRef,
        )
    }

    override suspend fun createDonationIntent(
        accessToken: String,
        eventId: String,
        comedianUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        idempotencyKey: String,
    ): Result<DonationIntent> {
        return donationBackendApi.createDonationIntent(
            accessToken = accessToken,
            eventId = eventId,
            comedianUserId = comedianUserId,
            amountMinor = amountMinor,
            currency = currency,
            message = message,
            idempotencyKey = idempotencyKey,
        )
    }

    override suspend fun listMyDonations(
        accessToken: String,
    ): Result<List<DonationIntent>> {
        return donationBackendApi.listMyDonations(accessToken = accessToken)
    }

    override suspend fun listMyReceivedDonations(
        accessToken: String,
    ): Result<List<DonationIntent>> {
        return donationBackendApi.listMyReceivedDonations(accessToken = accessToken)
    }
}
