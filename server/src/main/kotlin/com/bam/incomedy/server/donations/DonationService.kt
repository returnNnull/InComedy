package com.bam.incomedy.server.donations

import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import com.bam.incomedy.server.db.DonationRepository
import com.bam.incomedy.server.db.EventRepository
import com.bam.incomedy.server.db.LineupRepository
import com.bam.incomedy.server.db.SessionUserRepository
import com.bam.incomedy.server.db.StoredDonationIntent
import com.bam.incomedy.server.db.StoredPayoutProfile
import com.bam.incomedy.server.db.UserRole
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility

/**
 * Оркестрация первого backend slice-а donations/payouts.
 */
class DonationService(
    private val sessionUserRepository: SessionUserRepository,
    private val eventRepository: EventRepository,
    private val lineupRepository: LineupRepository,
    private val donationRepository: DonationRepository,
) {
    /** Текущий provider wire-name для первого manual-settlement-ready slice-а. */
    private val payoutProviderWireName = "manual_settlement"

    /** Возвращает self-service payout profile текущего комика, если он уже создан. */
    fun getMyPayoutProfile(
        actorUserId: String,
    ): StoredPayoutProfile? {
        requireComedianRole(actorUserId)
        return donationRepository.findPayoutProfile(actorUserId)
    }

    /** Создает или обновляет payout profile текущего комика. */
    fun upsertMyPayoutProfile(
        actorUserId: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ): StoredPayoutProfile {
        requireComedianRole(actorUserId)
        val existing = donationRepository.findPayoutProfile(actorUserId)
        val trimmedBeneficiaryRef = beneficiaryRef.trim()
        if (trimmedBeneficiaryRef.isBlank()) {
            throw DonationValidationException("beneficiary_ref_required")
        }
        val nextStatus = if (existing != null &&
            existing.legalType == legalType &&
            existing.beneficiaryRef == trimmedBeneficiaryRef
        ) {
            existing.verificationStatus
        } else {
            PayoutVerificationStatus.PENDING
        }
        return donationRepository.upsertPayoutProfile(
            userId = actorUserId,
            payoutProvider = payoutProviderWireName,
            legalType = legalType,
            beneficiaryRef = trimmedBeneficiaryRef,
            verificationStatus = nextStatus,
        )
    }

    /** Возвращает donation history текущего донатора. */
    fun listMyDonations(
        actorUserId: String,
    ): List<StoredDonationIntent> {
        requireAuthenticatedUser(actorUserId)
        return donationRepository.listDonationIntentsForDonor(actorUserId)
    }

    /** Возвращает donation history текущего комика. */
    fun listMyReceivedDonations(
        actorUserId: String,
    ): List<StoredDonationIntent> {
        requireComedianRole(actorUserId)
        return donationRepository.listDonationIntentsForComedian(actorUserId)
    }

    /** Создает provider-agnostic donation intent или возвращает предыдущий idempotent result. */
    fun createDonationIntent(
        actorUserId: String,
        eventId: String,
        comedianUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        idempotencyKey: String,
    ): CreateDonationIntentResult {
        requireAuthenticatedUser(actorUserId)
        if (actorUserId == comedianUserId) {
            throw DonationValidationException("self_donation_not_allowed")
        }
        if (amountMinor <= 0) {
            throw DonationValidationException("amount_minor_must_be_positive")
        }
        val normalizedCurrency = currency.trim().uppercase()
        if (normalizedCurrency.isBlank()) {
            throw DonationValidationException("currency_required")
        }
        val normalizedIdempotencyKey = idempotencyKey.trim()
        if (normalizedIdempotencyKey.isBlank()) {
            throw DonationValidationException("idempotency_key_required")
        }
        val normalizedMessage = message?.trim()?.takeIf(String::isNotBlank)
        if (normalizedMessage != null && normalizedMessage.length > 280) {
            throw DonationValidationException("message_too_long")
        }

        donationRepository.findDonationIntentByIdempotency(
            donorUserId = actorUserId,
            idempotencyKey = normalizedIdempotencyKey,
        )?.let { existing ->
            return CreateDonationIntentResult(
                donationIntent = existing,
                reusedExisting = true,
            )
        }

        val event = eventRepository.findEvent(eventId) ?: throw DonationEventNotFoundException(eventId)
        if (event.status != EventStatus.PUBLISHED.wireName || event.visibility != EventVisibility.PUBLIC.wireName) {
            throw DonationConflictException("donation_event_not_public")
        }
        if (event.currency != normalizedCurrency) {
            throw DonationValidationException("currency_mismatch")
        }
        val lineupEntry = lineupRepository.listEventLineup(eventId)
            .firstOrNull { it.comedianUserId == comedianUserId }
            ?: throw DonationConflictException("comedian_not_in_event_lineup")

        val payoutProfile = donationRepository.findPayoutProfile(comedianUserId)
            ?: throw DonationConflictException("comedian_payout_profile_missing")
        if (payoutProfile.verificationStatus != PayoutVerificationStatus.VERIFIED) {
            throw DonationConflictException("comedian_payout_not_verified")
        }

        val created = donationRepository.createDonationIntent(
            eventId = event.id,
            comedianUserId = lineupEntry.comedianUserId,
            donorUserId = actorUserId,
            amountMinor = amountMinor,
            currency = normalizedCurrency,
            message = normalizedMessage,
            status = DonationIntentStatus.CREATED,
            idempotencyKey = normalizedIdempotencyKey,
        )
        return CreateDonationIntentResult(
            donationIntent = created,
            reusedExisting = false,
        )
    }

    private fun requireAuthenticatedUser(
        actorUserId: String,
    ) {
        sessionUserRepository.findById(actorUserId)
            ?: throw DonationPermissionDeniedException("donation_actor_not_found")
    }

    private fun requireComedianRole(
        actorUserId: String,
    ) {
        val actor = sessionUserRepository.findById(actorUserId)
            ?: throw DonationPermissionDeniedException("donation_actor_not_found")
        if (UserRole.COMEDIAN !in actor.roles) {
            throw DonationPermissionDeniedException("comedian_role_required")
        }
    }
}

/**
 * Результат idempotent create donation intent flow.
 */
data class CreateDonationIntentResult(
    val donationIntent: StoredDonationIntent,
    val reusedExisting: Boolean,
)

/** Ошибка отсутствующего события donation surface-а. */
class DonationEventNotFoundException(
    val eventId: String,
) : IllegalStateException("Donation event was not found")

/** Ошибка прав доступа donation surface-а. */
class DonationPermissionDeniedException(
    val reasonCode: String,
) : IllegalStateException("Donation action is forbidden")

/** Ошибка конфликта состояния donation surface-а. */
class DonationConflictException(
    val reasonCode: String,
) : IllegalStateException("Donation state conflict")

/** Ошибка валидации donation surface-а. */
class DonationValidationException(
    val reasonCode: String,
) : IllegalArgumentException("Donation request validation failed")
