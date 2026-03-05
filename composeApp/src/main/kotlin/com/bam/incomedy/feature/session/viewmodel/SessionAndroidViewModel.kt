package com.bam.incomedy.feature.session.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bam.incomedy.shared.di.InComedyKoin
import com.bam.incomedy.shared.session.SessionState
import com.bam.incomedy.shared.session.SessionViewModel
import kotlinx.coroutines.flow.StateFlow

class SessionAndroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val sharedViewModel: SessionViewModel = InComedyKoin.getSessionViewModel()

    val state: StateFlow<SessionState> = sharedViewModel.state

    fun signOut() {
        sharedViewModel.signOut()
    }

    override fun onCleared() {
        super.onCleared()
        sharedViewModel.clear()
    }
}
