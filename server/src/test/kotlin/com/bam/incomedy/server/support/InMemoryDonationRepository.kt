package com.bam.incomedy.server.support

import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import com.bam.incomedy.server.db.DonationRepository
import com.bam.incomedy.server.db.StoredDonationIntent
import com.bam.incomedy.server.db.StoredPayoutProfile
import java.time.OffsetDateTime
import java.util.UUID

/**
 * In-memory реализация `DonationRepository` для route/service тестов.
 */
class InMemoryDonationRepository : DonationRepository {
    private val payoutProfilesByUserId = linkedMapOf<String, MutablePayoutProfileRecord>()
    private val donationIntentsById = linkedMapOf<String, MutableDonationIntentRecord>()

    override fun findPayoutProfile(userId: String): StoredPayoutProfile? {
        return payoutProfilesByUserId[userId]?.toStored()
    }

    override fun upsertPayoutProfile(
        userId: String,
        payoutProvider: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
        verificationStatus: PayoutVerificationStatus,
    ): StoredPayoutProfile {
        val existing = payoutProfilesByUserId[userId]
        val now = OffsetDateTime.now()
        val record = if (existing == null) {
            MutablePayoutProfileRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                payoutProvider = payoutProvider,
                legalType = legalType,
                beneficiaryRef = beneficiaryRef,
                verificationStatus = verificationStatus,
                createdAt = now,
                updatedAt = now,
                statusUpdatedAt = now,
            )
        } else {
            existing.payoutProvider = payoutProvider
            existing.legalType = legalType
            existing.beneficiaryRef = beneficiaryRef
            existing.updatedAt = now
            if (existing.verificationStatus != verificationStatus) {
                existing.verificationStatus = verificationStatus
                existing.statusUpdatedAt = now
            }
            existing
        }
        payoutProfilesByUserId[userId] = record
        return record.toStored()
    }

    override fun findDonationIntentByIdempotency(
        donorUserId: String,
        idempotencyKey: String,
    ): StoredDonationIntent? {
        return donationIntentsById.values
            .firstOrNull { it.donorUserId == donorUserId && it.idempotencyKey == idempotencyKey }
            ?.toStored()
    }

    override fun createDonationIntent(
        eventId: String,
        comedianUserId: String,
        donorUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        status: DonationIntentStatus,
        idempotencyKey: String,
    ): StoredDonationIntent {
        val now = OffsetDateTime.now()
        val record = MutableDonationIntentRecord(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            eventTitle = eventTitleById[eventId] ?: "Event",
            comedianUserId = comedianUserId,
            comedianDisplayName = userDisplayNameById[comedianUserId] ?: "Comedian",
            donorUserId = donorUserId,
            donorDisplayName = userDisplayNameById[donorUserId] ?: "Donor",
            amountMinor = amountMinor,
            currency = currency,
            message = message,
            status = status,
            paymentId = null,
            idempotencyKey = idempotencyKey,
            createdAt = now,
            updatedAt = now,
        )
        donationIntentsById[record.id] = record
        return record.toStored()
    }

    override fun listDonationIntentsForDonor(donorUserId: String): List<StoredDonationIntent> {
        return donationIntentsById.values
            .filter { it.donorUserId == donorUserId }
            .sortedByDescending { it.createdAt }
            .map(MutableDonationIntentRecord::toStored)
    }

    override fun listDonationIntentsForComedian(comedianUserId: String): List<StoredDonationIntent> {
        return donationIntentsById.values
            .filter { it.comedianUserId == comedianUserId }
            .sortedByDescending { it.createdAt }
            .map(MutableDonationIntentRecord::toStored)
    }

    fun seedVerifiedPayoutProfile(
        userId: String,
        legalType: PayoutLegalType = PayoutLegalType.SELF_EMPLOYED,
        beneficiaryRef: String = "beneficiary-ref",
    ) {
        val now = OffsetDateTime.now()
        payoutProfilesByUserId[userId] = MutablePayoutProfileRecord(
            id = UUID.randomUUID().toString(),
            userId = userId,
            payoutProvider = "manual_settlement",
            legalType = legalType,
            beneficiaryRef = beneficiaryRef,
            verificationStatus = PayoutVerificationStatus.VERIFIED,
            createdAt = now,
            updatedAt = now,
            statusUpdatedAt = now,
        )
    }

    fun bindUser(
        userId: String,
        displayName: String,
    ) {
        userDisplayNameById[userId] = displayName
    }

    fun bindEvent(
        eventId: String,
        title: String,
    ) {
        eventTitleById[eventId] = title
    }

    private val userDisplayNameById = linkedMapOf<String, String>()
    private val eventTitleById = linkedMapOf<String, String>()
}

private data class MutablePayoutProfileRecord(
    val id: String,
    val userId: String,
    var payoutProvider: String,
    var legalType: PayoutLegalType,
    var beneficiaryRef: String,
    var verificationStatus: PayoutVerificationStatus,
    val createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime,
    var statusUpdatedAt: OffsetDateTime,
) {
    fun toStored(): StoredPayoutProfile {
        return StoredPayoutProfile(
            id = id,
            userId = userId,
            payoutProvider = payoutProvider,
            legalType = legalType,
            beneficiaryRef = beneficiaryRef,
            verificationStatus = verificationStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
            statusUpdatedAt = statusUpdatedAt,
        )
    }
}

private data class MutableDonationIntentRecord(
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
    val status: DonationIntentStatus,
    val paymentId: String?,
    val idempotencyKey: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    fun toStored(): StoredDonationIntent {
        return StoredDonationIntent(
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
            status = status,
            paymentId = paymentId,
            idempotencyKey = idempotencyKey,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
