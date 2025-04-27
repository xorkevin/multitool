@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalStdlibApi::class)

package dev.xorkevin.multitool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dev.xorkevin.multitool.ui.theme.MultitoolTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
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
    object Tool {
        @Serializable
        object Home

        @Serializable
        data object Hash

        @Serializable
        data object PGP
    }
}

data class RouteEntry<T : Any>(val route: T, val name: String)

val routes = listOf(
    RouteEntry(Route.Tool.Hash, "Hash"),
    RouteEntry(Route.Tool.PGP, "PGP"),
)

@Composable
fun App() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val screenName = if (currentDestination != null) routes.find {
        currentDestination.hasRoute(it.route::class)
    }?.name ?: "Home" else "Home"

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Tools", modifier = Modifier.padding(16.dp))
                routes.forEach {
                    NavigationDrawerItem(
                        label = { Text(text = it.name) },
                        selected = currentDestination?.hasRoute(it.route::class) == true,
                        onClick = {
                            navController.navigate(route = it.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        },
                    )
                }

            }
        }) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = screenName) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu, contentDescription = "Tool Drawer"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Route.Tool.Home,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                navigation<Route.Tool.Home>(startDestination = Route.Tool.Hash) {
                    composable<Route.Tool.Hash> {
                        HashTool()
                    }
                    composable<Route.Tool.PGP> {
                        PGPTool()
                    }
                }
            }
        }
    }
}
