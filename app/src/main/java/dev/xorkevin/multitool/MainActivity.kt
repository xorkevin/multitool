@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package dev.xorkevin.multitool

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.xorkevin.multitool.ui.theme.MultitoolTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultitoolTheme {
                App()
            }
        }
    }
}

object Route {
    object Tools {
        @Serializable
        data object Home

        @Serializable
        data object Hash

        @Serializable
        data object PGPEncrypt

        @Serializable
        data object PGPDecrypt

        @Serializable
        data object QRScanner

        @Serializable
        data object Git

        @Serializable
        data object Biometrics
    }

    object Settings {
        @Serializable
        data object Home

        @Serializable
        data object RootKeyManager
    }
}

data class RouteEntry<T : Any>(val route: T, val name: String)

val topLevelRoutes = listOf(
    RouteEntry(Route.Tools.Home, "Home"),
    RouteEntry(Route.Settings.Home, "Settings"),
)

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val toggleNavDrawer: () -> Unit = remember(coroutineScope, drawerState) {
        {
            coroutineScope.launch {
                if (drawerState.isOpen) {
                    drawerState.close()
                } else {
                    drawerState.open()
                }
            }
        }
    }

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(), drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val currentDestination = currentBackStackEntry?.destination
                    Text(text = "Tools", modifier = Modifier.padding(16.dp))
                    topLevelRoutes.forEach {
                        NavigationDrawerItem(
                            label = { Text(text = it.name) },
                            selected = currentDestination?.hasRoute(it.route::class) == true,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                navController.navigate(route = it.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    restoreState = true
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
        }) {
        NavHost(
            navController = navController,
            startDestination = Route.Tools.Home,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<Route.Tools.Home> {
                ToolsNavHost(toggleNavDrawer)
            }
            composable<Route.Settings.Home> {
                SettingsNavHost(toggleNavDrawer)
            }
        }
    }
}
