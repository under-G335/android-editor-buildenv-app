package org.godotengine.godot_gradle_build_environment.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.godotengine.godot_gradle_build_environment.R
import org.godotengine.godot_gradle_build_environment.ui.navigation.NavigationDestination

object ProjectsDestination : NavigationDestination {
    override val route = "projects"
    override val titleRes = R.string.projects_screen_title
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    navigateToSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(ProjectsDestination.titleRes)) },
                actions = {
                    IconButton(onClick = navigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_button)
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            Text("Projects")
        }
    }
}
