package com.bam.incomedy.feature.donations

import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.PayoutProfile

/**
 * Single source of truth donations/payout overview surface-а.
 *
 * @property payoutProfile Текущий payout profile комика, если он уже создан.
 * @property sentDonations История донатов, отправленных текущим пользователем.
 * @property receivedDonations История донатов, полученных текущим комиком.
 * @property hasComedianRole Показывает, что текущая сессия имеет доступ к comedian payout surface.
 * @property isLoading Показывает первичную или ручную перезагрузку overview.
 * @property isSubmittingPayoutProfile Показывает активное сохранение payout profile.
 * @property errorMessage Безопасная ошибка для UI.
 */
data class DonationsState(
    val payoutProfile: PayoutProfile? = null,
    val sentDonations: List<DonationIntent> = emptyList(),
    val receivedDonations: List<DonationIntent> = emptyList(),
    val hasComedianRole: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmittingPayoutProfile: Boolean = false,
    val errorMessage: String? = null,
)
