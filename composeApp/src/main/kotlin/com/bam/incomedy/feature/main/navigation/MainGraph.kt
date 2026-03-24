package com.bam.incomedy.feature.main.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bam.incomedy.feature.event.viewmodel.EventAndroidViewModel
import com.bam.incomedy.feature.lineup.viewmodel.LineupAndroidViewModel
import com.bam.incomedy.feature.main.ui.MainScreen
import com.bam.incomedy.feature.session.viewmodel.SessionAndroidViewModel
import com.bam.incomedy.feature.ticketing.viewmodel.TicketingAndroidViewModel
import com.bam.incomedy.feature.venue.viewmodel.VenueAndroidViewModel
import com.bam.incomedy.navigation.AppGraph

/**
 * Добавляет основной граф Android-приложения с реальным post-auth экраном.
 *
 * @param sessionViewModel Android-адаптер расширенного session context.
 * @param venueViewModel Android-адаптер organizer venue management feature.
 */
fun NavGraphBuilder.mainGraph(
    sessionViewModel: SessionAndroidViewModel,
    eventViewModel: EventAndroidViewModel,
    lineupViewModel: LineupAndroidViewModel,
    ticketingViewModel: TicketingAndroidViewModel,
    venueViewModel: VenueAndroidViewModel,
) {
    mainGraph { modifier ->
        MainScreen(
            sessionViewModel = sessionViewModel,
            eventViewModel = eventViewModel,
            lineupViewModel = lineupViewModel,
            ticketingViewModel = ticketingViewModel,
            venueViewModel = venueViewModel,
            modifier = modifier,
        )
    }
}

/**
 * Добавляет основной граф Android-приложения с переданным Composable-содержимым.
 *
 * @param content Лямбда отрисовки post-auth экрана, удобная для тестов корневой навигации.
 */
fun NavGraphBuilder.mainGraph(
    content: @Composable (Modifier) -> Unit,
) {
    navigation(
        route = AppGraph.MAIN,
        startDestination = MainDestinations.MAIN,
    ) {
        composable(route = MainDestinations.MAIN) {
            content(Modifier.fillMaxSize())
        }
    }
}
