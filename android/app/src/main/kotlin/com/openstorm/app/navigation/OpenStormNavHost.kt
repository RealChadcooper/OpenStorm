package com.openstorm.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openstorm.app.ui.alerts.AlertsScreen
import com.openstorm.app.ui.radar.RadarScreen
import com.openstorm.app.ui.settings.SettingsScreen
import com.openstorm.app.ui.stations.StationsScreen

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Radar("radar", "Radar", Icons.Default.Map),
    Alerts("alerts", "Alerts", Icons.Default.NotificationsActive),
    Stations("stations", "Stations", Icons.Default.CellTower),
    Settings("settings", "Settings", Icons.Default.Settings),
}

@Composable
fun OpenStormNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Radar.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Radar.route) { RadarScreen() }
            composable(Screen.Alerts.route) { AlertsScreen() }
            composable(Screen.Stations.route) { StationsScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
