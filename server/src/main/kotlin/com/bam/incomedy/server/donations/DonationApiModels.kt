package com.bam.incomedy.server.donations

import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.server.db.StoredDonationIntent
import com.bam.incomedy.server.db.StoredPayoutProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val donationsJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class DonationIntentResponse(
    val id: String,
    @SerialName("event_id")
    val eventId: String,
    @SerialName("event_title")
    val eventTitle: String,
    @SerialName("comedian_user_id")
    val comedianUserId: String,
    @SerialName("comedian_display_name")
    val comedianDisplayName: String,
    @SerialName("donor_user_id")
    val donorUserId: String,
    @SerialName("donor_display_name")
    val donorDisplayName: String,
    @SerialName("amount_minor")
    val amountMinor: Int,
    val currency: String,
    val message: String? = null,
    val status: String,
    @SerialName("payment_id")
    val paymentId: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("checkout_available")
    val checkoutAvailable: Boolean = false,
) {
    companion object {
        fun fromStored(
            stored: StoredDonationIntent,
        ): DonationIntentResponse {
            return DonationIntentResponse(
                id = stored.id,
                eventId = stored.eventId,
                eventTitle = stored.eventTitle,
                comedianUserId = stored.comedianUserId,
                comedianDisplayName = stored.comedianDisplayName,
                donorUserId = stored.donorUserId,
                donorDisplayName = stored.donorDisplayName,
                amountMinor = stored.amountMinor,
                currency = stored.currency,
                message = stored.message,
                status = stored.status.wireName,
                paymentId = stored.paymentId,
                createdAt = stored.createdAt.toString(),
                updatedAt = stored.updatedAt.toString(),
            )
        }
    }
}

@Serializable
data class DonationIntentListResponse(
    val donations: List<DonationIntentResponse>,
)

@Serializable
data class PayoutProfileEnvelope(
    val profile: PayoutProfileResponse? = null,
)

@Serializable
data class PayoutProfileResponse(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("payout_provider")
    val payoutProvider: String,
    @SerialName("legal_type")
    val legalType: String,
    @SerialName("beneficiary_ref")
    val beneficiaryRef: String,
    @SerialName("verification_status")
    val verificationStatus: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("status_updated_at")
    val statusUpdatedAt: String,
) {
    companion object {
        fun fromStored(
            stored: StoredPayoutProfile,
        ): PayoutProfileResponse {
            return PayoutProfileResponse(
                id = stored.id,
                userId = stored.userId,
                payoutProvider = stored.payoutProvider,
                legalType = stored.legalType.wireName,
                beneficiaryRef = stored.beneficiaryRef,
                verificationStatus = stored.verificationStatus.wireName,
                createdAt = stored.createdAt.toString(),
                updatedAt = stored.updatedAt.toString(),
                statusUpdatedAt = stored.statusUpdatedAt.toString(),
            )
        }
    }
}

@Serializable
data class UpsertPayoutProfileRequest(
    @SerialName("legal_type")
    val legalType: String,
    @SerialName("beneficiary_ref")
    val beneficiaryRef: String,
)

@Serializable
data class CreateDonationIntentRequest(
    @SerialName("comedian_user_id")
    val comedianUserId: String,
    @SerialName("amount_minor")
    val amountMinor: Int,
    val currency: String,
    val message: String? = null,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
)

internal fun UpsertPayoutProfileRequest.toLegalTypeOrNull(): PayoutLegalType? {
    return PayoutLegalType.fromWireName(legalType.trim())
}
