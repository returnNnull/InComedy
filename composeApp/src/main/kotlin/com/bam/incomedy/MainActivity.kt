package com.bam.incomedy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthAndroidViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        authViewModel.onAuthCallbackUrl(intent?.dataString)
        setContent {
            App(authViewModel = authViewModel)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        authViewModel.onAuthCallbackUrl(intent.dataString)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
