package com.bam.incomedy.feature.donations

import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.DonationService
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared-координатор donation history и comedian payout surface-а.
 *
 * Модель загружает history текущего пользователя, а при наличии comedian-role дополняет ее
 * payout profile и received donations без выбора внешнего PSP.
 *
 * @property donationService Domain-сервис donations/payout backend API.
 * @property accessTokenProvider Провайдер текущего access token из app-level session state.
 * @property roleProvider Провайдер ролей текущей сессии для comedian-only surface.
 * @property dispatcher Dispatcher фоновых операций.
 */
class DonationsViewModel(
    private val donationService: DonationService,
    private val accessTokenProvider: () -> String?,
    private val roleProvider: () -> List<String>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow(DonationsState())
    val state: StateFlow<DonationsState> = _state.asStateFlow()

    /** Загружает donation overview для текущего пользователя. */
    fun loadOverview() {
        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            val hasComedianRole = roleProvider()
                .map(String::lowercase)
                .contains("comedian")
            _state.update {
                it.copy(
                    hasComedianRole = hasComedianRole,
                    isLoading = true,
                    errorMessage = null,
                )
            }

            var sentDonations = _state.value.sentDonations
            var receivedDonations = emptyList<DonationIntent>()
            var payoutProfile: PayoutProfile? = _state.value.payoutProfile
            var errorMessage: String? = null

            donationService.listMyDonations(accessToken).fold(
                onSuccess = { sentDonations = sortDonations(it) },
                onFailure = { throwable ->
                    errorMessage = throwable.message?.take(200) ?: "Не удалось загрузить отправленные донаты"
                },
            )

            if (hasComedianRole) {
                donationService.getMyPayoutProfile(accessToken).fold(
                    onSuccess = { payoutProfile = it },
                    onFailure = {
                        if (errorMessage == null) {
                            errorMessage = it.message?.take(200) ?: "Не удалось загрузить payout profile"
                        }
                    },
                )
                donationService.listMyReceivedDonations(accessToken).fold(
                    onSuccess = { receivedDonations = sortDonations(it) },
                    onFailure = {
                        if (errorMessage == null) {
                            errorMessage = it.message?.take(200) ?: "Не удалось загрузить полученные донаты"
                        }
                    },
                )
            } else {
                payoutProfile = null
            }

            _state.update {
                it.copy(
                    payoutProfile = payoutProfile,
                    sentDonations = sentDonations,
                    receivedDonations = receivedDonations,
                    hasComedianRole = hasComedianRole,
                    isLoading = false,
                    errorMessage = errorMessage,
                )
            }
        }
    }

    /** Сохраняет payout profile текущего комика. */
    fun savePayoutProfile(
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ) {
        val normalizedBeneficiaryRef = beneficiaryRef.trim()
        val hasComedianRole = roleProvider()
            .map(String::lowercase)
            .contains("comedian")
        if (!hasComedianRole) {
            _state.update {
                it.copy(
                    hasComedianRole = false,
                    errorMessage = "Payout profile доступен только роли комика",
                )
            }
            return
        }
        if (normalizedBeneficiaryRef.length !in 3..120) {
            _state.update {
                it.copy(errorMessage = "Укажите beneficiary ref длиной от 3 до 120 символов")
            }
            return
        }

        scope.launch {
            val accessToken = requireAccessToken() ?: return@launch
            _state.update {
                it.copy(
                    hasComedianRole = true,
                    isSubmittingPayoutProfile = true,
                    errorMessage = null,
                )
            }
            donationService.upsertMyPayoutProfile(
                accessToken = accessToken,
                legalType = legalType,
                beneficiaryRef = normalizedBeneficiaryRef,
            ).fold(
                onSuccess = { profile ->
                    _state.update {
                        it.copy(
                            payoutProfile = profile,
                            hasComedianRole = true,
                            isSubmittingPayoutProfile = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            hasComedianRole = true,
                            isSubmittingPayoutProfile = false,
                            errorMessage = error.message?.take(200) ?: "Не удалось сохранить payout profile",
                        )
                    }
                },
            )
        }
    }

    /** Скрывает текущую donations/payout error. */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun requireAccessToken(): String? {
        val accessToken = accessTokenProvider()
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (accessToken == null) {
            _state.update { it.copy(errorMessage = "Нет активной сессии для работы с донатами") }
        }
        return accessToken
    }

    private fun sortDonations(donations: List<DonationIntent>): List<DonationIntent> {
        return donations.sortedWith(
            compareByDescending<DonationIntent> { it.updatedAtIso }
                .thenByDescending { it.createdAtIso }
                .thenBy { it.id },
        )
    }
}
