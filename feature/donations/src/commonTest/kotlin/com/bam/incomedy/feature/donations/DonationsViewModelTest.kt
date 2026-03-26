package com.bam.incomedy.feature.donations

import com.bam.incomedy.domain.donations.DonationIntent
import com.bam.incomedy.domain.donations.DonationIntentStatus
import com.bam.incomedy.domain.donations.DonationService
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.domain.donations.PayoutProfile
import com.bam.incomedy.domain.donations.PayoutVerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DonationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadOverview loads sent and comedian-only data for comedian role`() = runTest(dispatcher) {
        val donationService = FakeDonationService(
            sentDonations = listOf(
                donationIntent(id = "sent-1", updatedAtIso = "2026-03-25T10:00:00+03:00"),
                donationIntent(id = "sent-2", updatedAtIso = "2026-03-26T10:00:00+03:00"),
            ),
            receivedDonations = listOf(
                donationIntent(id = "recv-1", donorDisplayName = "Анна"),
            ),
            payoutProfile = payoutProfile(),
        )
        val viewModel = DonationsViewModel(
            donationService = donationService,
            accessTokenProvider = { "access-token" },
            roleProvider = { listOf("audience", "comedian") },
            dispatcher = dispatcher,
        )

        viewModel.loadOverview()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasComedianRole)
        assertEquals("sent-2", state.sentDonations.first().id)
        assertEquals("recv-1", state.receivedDonations.single().id)
        assertEquals("profile-1", state.payoutProfile?.id)
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadOverview skips comedian-only endpoints for non-comedian role`() = runTest(dispatcher) {
        val donationService = FakeDonationService(
            sentDonations = listOf(donationIntent(id = "sent-1")),
            payoutProfileFailure = IllegalStateException("should not be called"),
            receivedDonationsFailure = IllegalStateException("should not be called"),
        )
        val viewModel = DonationsViewModel(
            donationService = donationService,
            accessTokenProvider = { "access-token" },
            roleProvider = { listOf("audience") },
            dispatcher = dispatcher,
        )

        viewModel.loadOverview()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.hasComedianRole)
        assertEquals(1, state.sentDonations.size)
        assertTrue(state.receivedDonations.isEmpty())
        assertNull(state.payoutProfile)
        assertEquals(0, donationService.getMyPayoutProfileCalls)
        assertEquals(0, donationService.listMyReceivedDonationsCalls)
    }

    @Test
    fun `savePayoutProfile validates beneficiary ref and stores updated profile`() = runTest(dispatcher) {
        val donationService = FakeDonationService(
            payoutProfile = payoutProfile(),
            upsertedPayoutProfile = payoutProfile(
                beneficiaryRef = "+79991234567",
                verificationStatus = PayoutVerificationStatus.PENDING,
            ),
        )
        val viewModel = DonationsViewModel(
            donationService = donationService,
            accessTokenProvider = { "access-token" },
            roleProvider = { listOf("comedian") },
            dispatcher = dispatcher,
        )

        viewModel.savePayoutProfile(
            legalType = PayoutLegalType.SELF_EMPLOYED,
            beneficiaryRef = "  +79991234567  ",
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("+79991234567", state.payoutProfile?.beneficiaryRef)
        assertFalse(state.isSubmittingPayoutProfile)
        assertNull(state.errorMessage)
        assertEquals(PayoutLegalType.SELF_EMPLOYED, donationService.lastSavedLegalType)
        assertEquals("+79991234567", donationService.lastSavedBeneficiaryRef)
    }

    private class FakeDonationService(
        private val sentDonations: List<DonationIntent> = emptyList(),
        private val receivedDonations: List<DonationIntent> = emptyList(),
        private val payoutProfile: PayoutProfile? = null,
        private val sentDonationsFailure: Throwable? = null,
        private val receivedDonationsFailure: Throwable? = null,
        private val payoutProfileFailure: Throwable? = null,
        private val upsertedPayoutProfile: PayoutProfile = payoutProfile(),
    ) : DonationService {
        var getMyPayoutProfileCalls: Int = 0
        var listMyReceivedDonationsCalls: Int = 0
        var lastSavedLegalType: PayoutLegalType? = null
        var lastSavedBeneficiaryRef: String? = null

        override suspend fun getMyPayoutProfile(accessToken: String): Result<PayoutProfile?> {
            getMyPayoutProfileCalls += 1
            return payoutProfileFailure?.let { Result.failure(it) } ?: Result.success(payoutProfile)
        }

        override suspend fun upsertMyPayoutProfile(
            accessToken: String,
            legalType: PayoutLegalType,
            beneficiaryRef: String,
        ): Result<PayoutProfile> {
            lastSavedLegalType = legalType
            lastSavedBeneficiaryRef = beneficiaryRef
            return Result.success(upsertedPayoutProfile)
        }

        override suspend fun createDonationIntent(
            accessToken: String,
            eventId: String,
            comedianUserId: String,
            amountMinor: Int,
            currency: String,
            message: String?,
            idempotencyKey: String,
        ): Result<DonationIntent> {
            error("Not needed in DonationsViewModelTest")
        }

        override suspend fun listMyDonations(accessToken: String): Result<List<DonationIntent>> {
            return sentDonationsFailure?.let { Result.failure(it) } ?: Result.success(sentDonations)
        }

        override suspend fun listMyReceivedDonations(accessToken: String): Result<List<DonationIntent>> {
            listMyReceivedDonationsCalls += 1
            return receivedDonationsFailure?.let { Result.failure(it) } ?: Result.success(receivedDonations)
        }
    }

    private companion object {
        fun payoutProfile(
            beneficiaryRef: String = "+79990000000",
            verificationStatus: PayoutVerificationStatus = PayoutVerificationStatus.VERIFIED,
        ): PayoutProfile {
            return PayoutProfile(
                id = "profile-1",
                userId = "comedian-1",
                payoutProvider = "manual_settlement",
                legalType = PayoutLegalType.SELF_EMPLOYED,
                beneficiaryRef = beneficiaryRef,
                verificationStatus = verificationStatus,
                createdAtIso = "2026-03-25T18:00:00+03:00",
                updatedAtIso = "2026-03-25T18:30:00+03:00",
                statusUpdatedAtIso = "2026-03-25T18:30:00+03:00",
            )
        }

        fun donationIntent(
            id: String,
            donorDisplayName: String = "Донатор",
            updatedAtIso: String = "2026-03-25T12:00:00+03:00",
        ): DonationIntent {
            return DonationIntent(
                id = id,
                eventId = "event-1",
                eventTitle = "Late Show",
                comedianUserId = "comedian-1",
                comedianDisplayName = "Иван Смехов",
                donorUserId = "donor-1",
                donorDisplayName = donorDisplayName,
                amountMinor = 2500,
                currency = "RUB",
                message = "Спасибо за сет",
                status = DonationIntentStatus.CREATED,
                createdAtIso = "2026-03-25T11:00:00+03:00",
                updatedAtIso = updatedAtIso,
            )
        }
    }
}
