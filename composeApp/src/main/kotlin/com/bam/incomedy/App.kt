package com.bam.incomedy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bam.incomedy.feature.auth.AuthModule
import com.bam.incomedy.feature.auth.ui.AuthScreen

@Composable
@Preview
fun App() {
    val authViewModel = remember { AuthModule.createViewModel() }

    MaterialTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            AuthScreen(viewModel = authViewModel)
        }
    }
}
