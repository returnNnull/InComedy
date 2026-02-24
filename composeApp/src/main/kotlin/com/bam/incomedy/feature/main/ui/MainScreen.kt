package com.bam.incomedy.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bam.incomedy.feature.auth.mvi.AuthIntent
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

@Composable
fun MainScreen(
    authViewModel: AuthAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Главная",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Сессия Telegram активна",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { authViewModel.onIntent(AuthIntent.OnSignOut) },
        ) {
            Text("Выйти")
        }
    }
}
