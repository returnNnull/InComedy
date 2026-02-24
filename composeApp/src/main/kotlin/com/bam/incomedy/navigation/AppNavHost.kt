package com.bam.incomedy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.bam.incomedy.feature.auth.navigation.authGraph
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.feature.main.navigation.mainGraph

@Composable
fun AppNavHost(
    authViewModel: AuthAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val state by authViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isAuthorized) {
        if (state.isAuthorized) {
            navigateToGraph(navController, AppGraph.MAIN)
        } else {
            navigateToGraph(navController, AppGraph.AUTH)
        }
    }

    NavHost(
        navController = navController,
        route = AppGraph.ROOT,
        startDestination = AppGraph.AUTH,
        modifier = modifier,
    ) {
        authGraph(authViewModel = authViewModel)
        mainGraph(authViewModel = authViewModel)
    }
}

private fun navigateToGraph(navController: NavHostController, route: String) {
    val currentRoute = navController.currentDestination?.route
    if (currentRoute == route) return
    navController.navigate(route) {
        popUpTo(AppGraph.ROOT) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
