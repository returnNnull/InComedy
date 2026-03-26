package com.bam.incomedy.feature.donations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile
import com.bam.incomedy.feature.donations.DonationsState
import com.bam.incomedy.shared.session.SessionState

internal data class DonationsTabBindings(
    val state: DonationsState = DonationsState(),
    val onRefresh: () -> Unit = {},
    val onSavePayoutProfile: (PayoutLegalType, String) -> Unit = { _, _ -> },
    val onClearError: () -> Unit = {},
)

/**
 * Вкладка donation history и comedian payout profile внутри Android shell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DonationHubTab(
    sessionState: SessionState,
    donationsBindings: DonationsTabBindings,
    modifier: Modifier = Modifier,
) {
    val donationsState = donationsBindings.state
    var selectedLegalType by rememberSaveable { mutableStateOf(PayoutLegalType.SELF_EMPLOYED.wireName) }
    var beneficiaryRef by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(sessionState.accessToken) {
        if (!sessionState.accessToken.isNullOrBlank()) {
            donationsBindings.onRefresh()
        }
    }

    LaunchedEffect(donationsState.payoutProfile?.id, donationsState.payoutProfile?.updatedAtIso) {
        donationsState.payoutProfile?.let { profile ->
            selectedLegalType = profile.legalType.wireName
            beneficiaryRef = profile.beneficiaryRef
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DonationScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Донаты и выплаты",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Provider-agnostic surface для payout profile и истории донатов без checkout и выбора PSP",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DonationsBanner(
            message = donationsState.errorMessage,
            onDismiss = donationsBindings.onClearError,
        )

        if (donationsState.isLoading || donationsState.isSubmittingPayoutProfile) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(DonationScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Отправлено: ${donationsState.sentDonations.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(DonationScreenTags.SENT_COUNT),
            )
            if (donationsState.hasComedianRole) {
                Text(
                    text = "Получено: ${donationsState.receivedDonations.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(DonationScreenTags.RECEIVED_COUNT),
                )
            }
            OutlinedButton(
                onClick = donationsBindings.onRefresh,
                enabled = !donationsState.isLoading && !donationsState.isSubmittingPayoutProfile,
                modifier = Modifier.testTag(DonationScreenTags.REFRESH_BUTTON),
            ) {
                Text("Обновить")
            }
        }

        if (donationsState.hasComedianRole) {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DonationScreenTags.PAYOUT_SECTION),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Профиль выплат комика",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = donationsState.payoutProfile?.let {
                            "Статус: ${payoutVerificationTitle(it)} · Провайдер: ${it.payoutProvider}"
                        } ?: "Профиль пока не заполнен. Первый foundation slice использует manual settlement.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(DonationScreenTags.PAYOUT_STATUS),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PayoutLegalType.entries.forEach { legalType ->
                            val isSelected = selectedLegalType == legalType.wireName
                            OutlinedButton(
                                onClick = { selectedLegalType = legalType.wireName },
                                enabled = !donationsState.isSubmittingPayoutProfile,
                                modifier = Modifier.testTag("${DonationScreenTags.PAYOUT_LEGAL_TYPE_PREFIX}${legalType.wireName}"),
                            ) {
                                Text(if (isSelected) "• ${payoutLegalTypeTitle(legalType)}" else payoutLegalTypeTitle(legalType))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = beneficiaryRef,
                        onValueChange = { beneficiaryRef = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DonationScreenTags.PAYOUT_BENEFICIARY_INPUT),
                        enabled = !donationsState.isSubmittingPayoutProfile,
                        label = { Text("Beneficiary ref") },
                        placeholder = { Text("Телефон, ИНН или другой payout identifier") },
                    )
                    Button(
                        onClick = {
                            val legalType = PayoutLegalType.fromWireName(selectedLegalType)
                                ?: PayoutLegalType.SELF_EMPLOYED
                            donationsBindings.onSavePayoutProfile(legalType, beneficiaryRef)
                        },
                        enabled = beneficiaryRef.trim().isNotBlank() && !donationsState.isSubmittingPayoutProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DonationScreenTags.PAYOUT_SAVE_BUTTON),
                    ) {
                        Text("Сохранить payout profile")
                    }
                    donationsState.payoutProfile?.let { profile ->
                        PayoutProfileCard(profile = profile)
                    }
                }
            }
        } else {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DonationScreenTags.PAYOUT_LOCKED),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Payout profile станет доступен после выдачи роли комика",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "До этого здесь остается только audience-side история отправленных донатов.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        HorizontalDivider()
        DonationListSection(
            title = "Мои отправленные донаты",
            donations = donationsState.sentDonations,
            emptyTag = DonationScreenTags.SENT_EMPTY_STATE,
        )

        if (donationsState.hasComedianRole) {
            HorizontalDivider()
            DonationListSection(
                title = "Полученные донаты",
                donations = donationsState.receivedDonations,
                emptyTag = DonationScreenTags.RECEIVED_EMPTY_STATE,
            )
        }
    }
}

@Composable
private fun DonationsBanner(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DonationScreenTags.ERROR_BANNER),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = onDismiss) {
                Text("Скрыть")
            }
        }
    }
}

@Composable
private fun PayoutProfileCard(
    profile: PayoutProfile,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DonationScreenTags.PAYOUT_PROFILE_CARD),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Актуальный payout profile",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Юртип: ${payoutLegalTypeTitle(profile.legalType)}")
            Text("Beneficiary ref: ${profile.beneficiaryRef}")
            Text("Статус проверки: ${payoutVerificationTitle(profile)}")
            Text("Обновлён: ${profile.updatedAtIso}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DonationListSection(
    title: String,
    donations: List<DonationIntent>,
    emptyTag: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (donations.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(emptyTag),
            ) {
                Text(
                    text = "Пока пусто",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            donations.forEach { donation ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${DonationScreenTags.DONATION_CARD_PREFIX}${donation.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${donation.comedianDisplayName} · ${formatAmountMinor(donation.amountMinor, donation.currency)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Событие: ${donation.eventTitle}")
                        Text("Донатор: ${donation.donorDisplayName}")
                        Text("Статус: ${donationStatusTitle(donation)}")
                        donation.message?.let { Text("Сообщение: $it") }
                    }
                }
            }
        }
    }
}

internal object DonationScreenTags {
    const val ROOT = "donations.root"
    const val LOADING = "donations.loading"
    const val ERROR_BANNER = "donations.error"
    const val REFRESH_BUTTON = "donations.refresh"
    const val SENT_COUNT = "donations.count.sent"
    const val RECEIVED_COUNT = "donations.count.received"
    const val PAYOUT_SECTION = "donations.payout.section"
    const val PAYOUT_LOCKED = "donations.payout.locked"
    const val PAYOUT_STATUS = "donations.payout.status"
    const val PAYOUT_BENEFICIARY_INPUT = "donations.payout.beneficiary"
    const val PAYOUT_SAVE_BUTTON = "donations.payout.save"
    const val PAYOUT_PROFILE_CARD = "donations.payout.card"
    const val PAYOUT_LEGAL_TYPE_PREFIX = "donations.payout.legalType."
    const val SENT_EMPTY_STATE = "donations.sent.empty"
    const val RECEIVED_EMPTY_STATE = "donations.received.empty"
    const val DONATION_CARD_PREFIX = "donations.card."
}

private fun payoutLegalTypeTitle(legalType: PayoutLegalType): String {
    return when (legalType) {
        PayoutLegalType.INDIVIDUAL -> "Физлицо"
        PayoutLegalType.SELF_EMPLOYED -> "Самозанятый"
        PayoutLegalType.SOLE_PROPRIETOR -> "ИП"
        PayoutLegalType.COMPANY -> "Компания"
    }
}

private fun payoutVerificationTitle(profile: PayoutProfile): String {
    return when (profile.verificationStatus.wireName) {
        "pending" -> "На проверке"
        "verified" -> "Проверен"
        "rejected" -> "Отклонён"
        "blocked" -> "Заблокирован"
        else -> profile.verificationStatus.wireName
    }
}

private fun donationStatusTitle(donation: DonationIntent): String {
    return when (donation.status.wireName) {
        "created" -> "Создан"
        "awaiting_payment" -> "Ожидает оплату"
        "paid" -> "Оплачен"
        "payout_pending" -> "Ожидает выплату"
        "paid_out" -> "Выплачен"
        "failed" -> "Ошибка"
        "reversed" -> "Отменён"
        else -> donation.status.wireName
    }
}

private fun formatAmountMinor(amountMinor: Int, currency: String): String {
    val major = amountMinor / 100
    val minor = amountMinor % 100
    return "%s %d.%02d".format(currency, major, minor)
}
