package com.bam.incomedy.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.bam.incomedy.feature.auth.navigation.authGraph
import com.bam.incomedy.feature.auth.ui.AuthScreen
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.feature.main.navigation.mainGraph
import com.bam.incomedy.feature.main.ui.MainScreen
import com.bam.incomedy.feature.event.viewmodel.EventAndroidViewModel
import com.bam.incomedy.feature.lineup.viewmodel.LineupAndroidViewModel
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import com.bam.incomedy.feature.ticketing.viewmodel.TicketingAndroidViewModel
import com.bam.incomedy.feature.venue.viewmodel.VenueAndroidViewModel
import com.bam.incomedy.viewmodel.AndroidViewModelFactories

/**
 * Корневой Compose navigation host Android-приложения.
 *
 * @param authViewModel Android-адаптер авторизации, управляющий переходом между auth и main графами.
 * @param modifier Внешний модификатор `NavHost`.
 */
@Composable
fun AppNavHost(
    authViewModel: AuthAndroidViewModel,
    modifier: Modifier = Modifier,
) {
    /** Application context, используемый для явного создания session `ViewModel`. */
    val application = LocalContext.current.applicationContext as Application
    val navController = rememberNavController()
    val sessionViewModel: SessionAndroidViewModel = viewModel(
        factory = AndroidViewModelFactories.session(application),
    )
    val eventViewModel: EventAndroidViewModel = viewModel(
        factory = AndroidViewModelFactories.event(application),
    )
    val ticketingViewModel: TicketingAndroidViewModel = viewModel(
        factory = AndroidViewModelFactories.ticketing(application),
    )
    val lineupViewModel: LineupAndroidViewModel = viewModel(
        factory = AndroidViewModelFactories.lineup(application),
    )
    val venueViewModel: VenueAndroidViewModel = viewModel(
        factory = AndroidViewModelFactories.venue(application),
    )
    val state by authViewModel.state.collectAsStateWithLifecycle()

    AppNavHostContent(
        isAuthorized = state.isAuthorized,
        navController = navController,
        authContent = { contentModifier ->
            AuthScreen(
                viewModel = authViewModel,
                modifier = contentModifier,
            )
        },
        mainContent = { contentModifier ->
            MainScreen(
                sessionViewModel = sessionViewModel,
                eventViewModel = eventViewModel,
                lineupViewModel = lineupViewModel,
                ticketingViewModel = ticketingViewModel,
                venueViewModel = venueViewModel,
                modifier = contentModifier,
            )
        },
        modifier = modifier,
    )
}

/**
 * Тестируемое содержимое корневой Android-навигации без прямой зависимости от Android `ViewModel`.
 *
 * @param isAuthorized Показывает, должен ли пользователь видеть auth- или main-граф.
 * @param authContent Composable-содержимое auth-графа.
 * @param mainContent Composable-содержимое основного графа.
 * @param modifier Внешний модификатор `NavHost`.
 * @param navController Контроллер навигации, который можно подменить в тестах.
 */
@Composable
internal fun AppNavHostContent(
    isAuthorized: Boolean,
    authContent: @Composable (Modifier) -> Unit,
    mainContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(isAuthorized) {
        if (isAuthorized) {
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
        authGraph(content = authContent)
        mainGraph(content = mainContent)
    }
}

/**
 * Переключает верхнеуровневый граф приложения только при фактической смене auth-состояния.
 *
 * @param navController Контроллер навигации приложения.
 * @param route Целевой верхнеуровневый граф.
 */
private fun navigateToGraph(navController: NavHostController, route: String) {
    val isAlreadyInTargetGraph = (
        navController.currentDestination
            ?.hierarchy
            ?.any { destination -> destination.route == route }
        ) == true
    if (isAlreadyInTargetGraph) return
    navController.navigate(route) {
        popUpTo(AppGraph.ROOT) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
