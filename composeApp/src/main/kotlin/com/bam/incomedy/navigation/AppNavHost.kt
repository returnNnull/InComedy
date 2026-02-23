package com.bam.incomedy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.bam.incomedy.feature.auth.navigation.authGraph
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel

@Composable
fun AppNavHost(
    authViewModel: AuthAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        route = AppGraph.ROOT,
        startDestination = AppGraph.AUTH,
        modifier = modifier,
    ) {
        authGraph(authViewModel = authViewModel)
    }
}
