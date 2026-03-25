package com.bam.incomedy.domain.donations

/**
 * Самостоятельный donation intent вне ticket checkout.
 *
 * @property id Идентификатор donation intent.
 * @property eventId Идентификатор события-владельца.
 * @property eventTitle Человекочитаемое имя события для истории.
 * @property comedianUserId Идентификатор комика-получателя.
 * @property comedianDisplayName Отображаемое имя комика.
 * @property donorUserId Идентификатор пользователя-донатора.
 * @property donorDisplayName Отображаемое имя донатора.
 * @property amountMinor Сумма доната в minor units.
 * @property currency Валюта доната.
 * @property message Необязательное сообщение донатора.
 * @property status Текущий lifecycle donation intent-а.
 * @property createdAtIso RFC3339 timestamp создания записи.
 * @property updatedAtIso RFC3339 timestamp последнего изменения записи.
 */
data class DonationIntent(
    val id: String,
    val eventId: String,
    val eventTitle: String,
    val comedianUserId: String,
    val comedianDisplayName: String,
    val donorUserId: String,
    val donorDisplayName: String,
    val amountMinor: Int,
    val currency: String,
    val message: String? = null,
    val status: DonationIntentStatus,
    val createdAtIso: String,
    val updatedAtIso: String,
)

/**
 * Статусы payout profile комика.
 *
 * @property wireName Стабильное wire/persistence значение статуса.
 */
enum class PayoutVerificationStatus(
    val wireName: String,
) {
    PENDING("pending"),
    VERIFIED("verified"),
    REJECTED("rejected"),
    BLOCKED("blocked"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): PayoutVerificationStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Поддерживаемые типы payout identity для MVP foundation.
 *
 * @property wireName Стабильное wire/persistence значение типа.
 */
enum class PayoutLegalType(
    val wireName: String,
) {
    INDIVIDUAL("individual"),
    SELF_EMPLOYED("self_employed"),
    SOLE_PROPRIETOR("sole_proprietor"),
    COMPANY("company"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): PayoutLegalType? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}

/**
 * Self-service payout profile комика.
 *
 * @property id Идентификатор payout profile.
 * @property userId Идентификатор комика-владельца.
 * @property payoutProvider Текущий payout provider wire-name; для первого slice-а это `manual_settlement`.
 * @property legalType Юртип получателя.
 * @property beneficiaryRef Provider-specific reference, введенный комиком.
 * @property verificationStatus Текущий verification/compliance статус.
 * @property createdAtIso RFC3339 timestamp создания записи.
 * @property updatedAtIso RFC3339 timestamp последнего изменения записи.
 * @property statusUpdatedAtIso RFC3339 timestamp последнего изменения verification статуса.
 */
data class PayoutProfile(
    val id: String,
    val userId: String,
    val payoutProvider: String,
    val legalType: PayoutLegalType,
    val beneficiaryRef: String,
    val verificationStatus: PayoutVerificationStatus,
    val createdAtIso: String,
    val updatedAtIso: String,
    val statusUpdatedAtIso: String,
)

/**
 * Lifecycle donation intent-а.
 *
 * @property wireName Стабильное wire/persistence значение статуса.
 */
enum class DonationIntentStatus(
    val wireName: String,
) {
    CREATED("created"),
    AWAITING_PAYMENT("awaiting_payment"),
    PAID("paid"),
    PAYOUT_PENDING("payout_pending"),
    PAID_OUT("paid_out"),
    FAILED("failed"),
    REVERSED("reversed"),
    ;

    companion object {
        /** Восстанавливает enum по wire-значению. */
        fun fromWireName(value: String): DonationIntentStatus? {
            return entries.firstOrNull { it.wireName == value }
        }
    }
}
