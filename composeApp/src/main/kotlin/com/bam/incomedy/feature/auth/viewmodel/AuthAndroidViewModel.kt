package com.bam.incomedy.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import com.bam.incomedy.feature.auth.mvi.AuthEffect
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.mvi.AuthState
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class AuthAndroidViewModel(
    private val sharedViewModel: AuthViewModel = InComedyKoin.getAuthViewModel(),
) : ViewModel() {
    val state: StateFlow<AuthState> = sharedViewModel.state
    val effects: SharedFlow<AuthEffect> = sharedViewModel.effects

    fun onIntent(intent: AuthIntent) {
        sharedViewModel.onIntent(intent)
    }

    override fun onCleared() {
        super.onCleared()
        sharedViewModel.clear()
    }
}
