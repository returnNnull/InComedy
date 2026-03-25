package com.bam.incomedy.server.db

import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import java.time.OffsetDateTime

/**
 * Self-service payout profile комика.
 */
data class StoredPayoutProfile(
    val id: String,
    val userId: String,
    val payoutProvider: String,
    val legalType: PayoutLegalType,
    val beneficiaryRef: String,
    val verificationStatus: PayoutVerificationStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val statusUpdatedAt: OffsetDateTime,
)

/**
 * Provider-agnostic donation intent read model.
 */
data class StoredDonationIntent(
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
)

/**
 * Persistence-контракт первого backend slice-а donations/payouts.
 */
interface DonationRepository {
    /** Возвращает payout profile комика по user id. */
    fun findPayoutProfile(userId: String): StoredPayoutProfile?

    /** Создает или обновляет payout profile комика. */
    fun upsertPayoutProfile(
        userId: String,
        payoutProvider: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
        verificationStatus: PayoutVerificationStatus,
    ): StoredPayoutProfile

    /** Возвращает donation intent по idempotency key конкретного донатора. */
    fun findDonationIntentByIdempotency(
        donorUserId: String,
        idempotencyKey: String,
    ): StoredDonationIntent?

    /** Создает новый donation intent. */
    fun createDonationIntent(
        eventId: String,
        comedianUserId: String,
        donorUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        status: DonationIntentStatus,
        idempotencyKey: String,
    ): StoredDonationIntent

    /** Возвращает donation history донатора в обратном хронологическом порядке. */
    fun listDonationIntentsForDonor(
        donorUserId: String,
    ): List<StoredDonationIntent>

    /** Возвращает donation history комика в обратном хронологическом порядке. */
    fun listDonationIntentsForComedian(
        comedianUserId: String,
    ): List<StoredDonationIntent>
}
