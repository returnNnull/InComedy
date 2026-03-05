package com.bam.incomedy.feature.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel

@Composable
fun MainScreen(
    sessionViewModel: SessionAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    val state by sessionViewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Главная",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = state.displayName ?: "Сессия активна",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { sessionViewModel.signOut() },
        ) {
            Text("Выйти")
        }
    }
}
