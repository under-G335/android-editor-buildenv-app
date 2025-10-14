package org.godotengine.godot_gradle_build_environment.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.godotengine.godot_gradle_build_environment.ui.screens.ProjectsDestination
import org.godotengine.godot_gradle_build_environment.ui.screens.ProjectsScreen
import org.godotengine.godot_gradle_build_environment.ui.screens.SettingsDestination
import org.godotengine.godot_gradle_build_environment.ui.screens.SettingsScreen

@Composable
fun NavigationHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = ProjectsDestination.route
    ) {
        composable(route = ProjectsDestination.route) {
            ProjectsScreen(
                navigateToSettings = {
                    navController.navigate(SettingsDestination.route)
                }
            )
        }
        composable(route = SettingsDestination.route) {
            SettingsScreen(
                navigateUp = { navController.navigateUp() }
            )
        }
    }
}
