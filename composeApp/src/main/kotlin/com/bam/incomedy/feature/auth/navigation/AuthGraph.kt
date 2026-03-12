package com.bam.incomedy.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.bam.incomedy.feature.auth.ui.AuthScreen
import com.bam.incomedy.feature.auth.viewmodel.AuthAndroidViewModel
import com.bam.incomedy.navigation.AppGraph

/**
 * Добавляет auth-граф Android-приложения с реальным экраном авторизации.
 *
 * @param authViewModel Android-адаптер авторизации.
 */
fun NavGraphBuilder.authGraph(
    authViewModel: AuthAndroidViewModel,
) {
    authGraph { modifier ->
        AuthScreen(
            viewModel = authViewModel,
            modifier = modifier,
        )
    }
}

/**
 * Добавляет auth-граф Android-приложения с переданным Composable-содержимым.
 *
 * @param content Лямбда отрисовки auth-экрана, удобная для тестов корневой навигации.
 */
fun NavGraphBuilder.authGraph(
    content: @Composable (Modifier) -> Unit,
) {
    navigation(
        route = AppGraph.AUTH,
        startDestination = AuthDestinations.AUTH,
    ) {
        composable(route = AuthDestinations.AUTH) {
            content(Modifier.fillMaxSize())
        }
    }
}
