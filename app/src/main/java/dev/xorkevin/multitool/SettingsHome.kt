@file:OptIn(ExperimentalMaterial3Api::class)

package dev.xorkevin.multitool

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

val settingsRoutes = listOf(
    RouteEntry(Route.Settings.RootKeyManager, "Manage Root Key"),
)

@Composable
fun SettingsNavHost(toggleNavDrawer: () -> Unit) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val navigate: (route: Any) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route = route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                restoreState = true
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = currentBackStackEntry?.destination?.let { dest ->
                        settingsRoutes.find {
                            dest.hasRoute(
                                it.route::class
                            )
                        }?.name
                    } ?: "Settings")
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.hasRoute(Route.Settings.Home::class)
                            ?: true
                    ) {
                        IconButton(onClick = toggleNavDrawer) {
                            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Navigation")
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Settings.Home,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            composable<Route.Settings.Home> {
                SettingsHome(navigate)
            }
            composable<Route.Settings.RootKeyManager> {
                RootKeyManager()
            }
        }
    }
}

@Composable
fun SettingsHome(navigate: (route: Any) -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        settingsRoutes.forEach {
            ListItem(headlineContent = {
                Text(text = it.name)
            }, modifier = Modifier.clickable(onClick = { navigate(it.route) }))
        }
    }
}
