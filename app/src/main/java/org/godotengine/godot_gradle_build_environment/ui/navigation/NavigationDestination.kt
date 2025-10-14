package org.godotengine.godot_gradle_build_environment.ui.navigation

interface NavigationDestination {

    /**
     * Unique name to define the path for a composable.
     */
    val route: String

    /**
     * String resource id that contains title to be displayed on the screen.
     */
    val titleRes: Int

}
