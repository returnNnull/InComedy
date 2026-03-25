package com.bam.incomedy.data.donations.backend

import com.bam.incomedy.core.backend.BackendEnvironment
import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.core.backend.bearer
import com.bam.incomedy.core.backend.createBackendHttpClient
import com.bam.incomedy.core.backend.ensureBackendSuccess
import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP-клиент provider-agnostic donations/payout foundation API.
 */
class DonationBackendApi(
    private val baseUrl: String = BackendEnvironment.baseUrl,
    private val parser: Json = backendJson,
    private val httpClient: HttpClient = createBackendHttpClient(parser),
) {
    /** Возвращает payout profile текущего комика, если он уже создан. */
    suspend fun getMyPayoutProfile(
        accessToken: String,
    ): Result<PayoutProfile?> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/comedian/me/payout-profile") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<PayoutProfileEnvelope>().profile?.toDomain()
        }
    }

    /** Создает или обновляет payout profile текущего комика. */
    suspend fun upsertMyPayoutProfile(
        accessToken: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ): Result<PayoutProfile> {
        return runCatching {
            val response = httpClient.put("$baseUrl/api/v1/comedian/me/payout-profile") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    UpsertPayoutProfileRequest(
                        legalType = legalType.wireName,
                        beneficiaryRef = beneficiaryRef,
                    ),
                )
            }
            ensureBackendSuccess(response, parser)
            response.body<PayoutProfileResponse>().toDomain()
        }
    }

    /** Создает donation intent для выбранного комика на событии. */
    suspend fun createDonationIntent(
        accessToken: String,
        eventId: String,
        comedianUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        idempotencyKey: String,
    ): Result<DonationIntent> {
        return runCatching {
            val response = httpClient.post("$baseUrl/api/v1/events/$eventId/donations") {
                bearer(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    CreateDonationIntentRequest(
                        comedianUserId = comedianUserId,
                        amountMinor = amountMinor,
                        currency = currency,
                        message = message,
                        idempotencyKey = idempotencyKey,
                    ),
                )
            }
            ensureBackendSuccess(response, parser)
            response.body<DonationIntentResponse>().toDomain()
        }
    }

    /** Возвращает donation history текущего донатора. */
    suspend fun listMyDonations(
        accessToken: String,
    ): Result<List<DonationIntent>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/me/donations") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<DonationIntentListResponse>().donations.map(DonationIntentResponse::toDomain)
        }
    }

    /** Возвращает donation history текущего комика. */
    suspend fun listMyReceivedDonations(
        accessToken: String,
    ): Result<List<DonationIntent>> {
        return runCatching {
            val response = httpClient.get("$baseUrl/api/v1/comedian/me/donations") {
                bearer(accessToken)
            }
            ensureBackendSuccess(response, parser)
            response.body<DonationIntentListResponse>().donations.map(DonationIntentResponse::toDomain)
        }
    }
}

@Serializable
private data class DonationIntentListResponse(
    val donations: List<DonationIntentResponse>,
)

@Serializable
private data class DonationIntentResponse(
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
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
) {
    fun toDomain(): DonationIntent {
        return DonationIntent(
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
            status = requireNotNull(DonationIntentStatus.fromWireName(status)),
            createdAtIso = createdAt,
            updatedAtIso = updatedAt,
        )
    }
}

@Serializable
private data class PayoutProfileEnvelope(
    val profile: PayoutProfileResponse? = null,
)

@Serializable
private data class PayoutProfileResponse(
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
    fun toDomain(): PayoutProfile {
        return PayoutProfile(
            id = id,
            userId = userId,
            payoutProvider = payoutProvider,
            legalType = requireNotNull(PayoutLegalType.fromWireName(legalType)),
            beneficiaryRef = beneficiaryRef,
            verificationStatus = requireNotNull(PayoutVerificationStatus.fromWireName(verificationStatus)),
            createdAtIso = createdAt,
            updatedAtIso = updatedAt,
            statusUpdatedAtIso = statusUpdatedAt,
        )
    }
}

@Serializable
private data class UpsertPayoutProfileRequest(
    @SerialName("legal_type")
    val legalType: String,
    @SerialName("beneficiary_ref")
    val beneficiaryRef: String,
)

@Serializable
private data class CreateDonationIntentRequest(
    @SerialName("comedian_user_id")
    val comedianUserId: String,
    @SerialName("amount_minor")
    val amountMinor: Int,
    val currency: String,
    val message: String? = null,
    @SerialName("idempotency_key")
    val idempotencyKey: String,
)
