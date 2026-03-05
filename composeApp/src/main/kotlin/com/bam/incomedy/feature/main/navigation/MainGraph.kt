package com.bam.incomedy.feature.main.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bam.incomedy.feature.main.ui.MainScreen
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import com.bam.incomedy.navigation.AppGraph

fun NavGraphBuilder.mainGraph(
    sessionViewModel: SessionAndroidViewModel,
) {
    navigation(
        route = AppGraph.MAIN,
        startDestination = MainDestinations.MAIN,
    ) {
        composable(route = MainDestinations.MAIN) {
            MainScreen(
                sessionViewModel = sessionViewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
