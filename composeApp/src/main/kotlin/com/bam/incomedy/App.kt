package com.bam.incomedy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bam.incomedy.navigation.AppNavHost
import com.bam.incomedy.shared.di.InComedyKoin

@Composable
@Preview
fun App() {
    val authViewModel = remember {
        InComedyKoin.getAuthViewModel()
    }

    MaterialTheme {
        AppNavHost(
            authViewModel = authViewModel,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
        )
    }
}
