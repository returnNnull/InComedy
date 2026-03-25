package com.bam.incomedy.domain.donations

/**
 * Контракт provider-agnostic foundation slice-а donations/payouts.
 */
interface DonationService {
    /** Возвращает payout profile текущего комика, если он уже создан. */
    suspend fun getMyPayoutProfile(
        accessToken: String,
    ): Result<PayoutProfile?>

    /** Создает или обновляет payout profile текущего комика. */
    suspend fun upsertMyPayoutProfile(
        accessToken: String,
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ): Result<PayoutProfile>

    /** Создает provider-agnostic donation intent для выбранного комика на событии. */
    suspend fun createDonationIntent(
        accessToken: String,
        eventId: String,
        comedianUserId: String,
        amountMinor: Int,
        currency: String,
        message: String?,
        idempotencyKey: String,
    ): Result<DonationIntent>

    /** Возвращает donation history текущего донатора. */
    suspend fun listMyDonations(
        accessToken: String,
    ): Result<List<DonationIntent>>

    /** Возвращает donation history текущего комика. */
    suspend fun listMyReceivedDonations(
        accessToken: String,
    ): Result<List<DonationIntent>>
}
