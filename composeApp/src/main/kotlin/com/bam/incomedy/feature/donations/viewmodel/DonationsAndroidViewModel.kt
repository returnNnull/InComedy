package com.bam.incomedy.feature.donations.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.domain.donations.PayoutLegalType
import com.bam.incomedy.feature.donations.DonationsState
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-адаптер общей модели donation overview и comedian payout surface.
 */
class DonationsAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val sharedViewModel = InComedyKoin.getDonationsViewModel()
    val state: StateFlow<DonationsState> = sharedViewModel.state

    fun refresh() {
        sharedViewModel.loadOverview()
    }

    fun savePayoutProfile(
        legalType: PayoutLegalType,
        beneficiaryRef: String,
    ) {
        sharedViewModel.savePayoutProfile(
            legalType = legalType,
            beneficiaryRef = beneficiaryRef,
        )
    }

    fun clearError() {
        sharedViewModel.clearError()
    }
}
