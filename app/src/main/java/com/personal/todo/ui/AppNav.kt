package com.personal.todo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.todo.AppContainer

@Composable
private inline fun <reified T : ViewModel> containerViewModel(crossinline create: () -> T): T =
    viewModel(factory = viewModelFactory { initializer { create() } })

@Composable
fun AppNav(container: AppContainer, openTask: MutableState<Long?>) {
    val nav = rememberNavController()

    // Tapping a reminder notification opens that task.
    LaunchedEffect(openTask.value) {
        openTask.value?.let {
            nav.navigate("edit/$it") { launchSingleTop = true }
            openTask.value = null
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = containerViewModel { HomeViewModel(container) },
                onAdd = { nav.navigate("edit/0") },
                onOpen = { id -> nav.navigate("edit/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("edit/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("taskId") ?: 0L
            EditTaskScreen(
                vm = containerViewModel { EditViewModel(container, id) },
                onClose = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                vm = containerViewModel { SettingsViewModel(container) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
