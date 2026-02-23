package com.bam.incomedy.feature.auth.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bam.incomedy.feature.auth.mvi.AuthViewModel
import com.bam.incomedy.feature.auth.ui.AuthScreen
import com.bam.incomedy.navigation.AppGraph

fun NavGraphBuilder.authGraph(
    authViewModel: AuthViewModel,
) {
    navigation(
        route = AppGraph.AUTH,
        startDestination = AuthDestinations.AUTH,
    ) {
        composable(route = AuthDestinations.AUTH) {
            AuthScreen(
                viewModel = authViewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
