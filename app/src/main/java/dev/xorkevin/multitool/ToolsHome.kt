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

val toolsRoutes = listOf(
    RouteEntry(Route.Tools.Hash, "Hash"),
    RouteEntry(Route.Tools.PGPEncrypt, "PGP Encrypt"),
    RouteEntry(Route.Tools.PGPDecrypt, "PGP Decrypt"),
    RouteEntry(Route.Tools.QRScanner, "QR Scanner"),
    RouteEntry(Route.Tools.Git, "Git"),
    RouteEntry(Route.Tools.Biometrics, "Biometrics"),
)

@Composable
fun ToolsNavHost(toggleNavDrawer: () -> Unit) {
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
                        toolsRoutes.find {
                            dest.hasRoute(
                                it.route::class
                            )
                        }?.name
                    } ?: "Tools")
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.hasRoute(Route.Tools.Home::class)
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
            startDestination = Route.Tools.Home,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            composable<Route.Tools.Home> {
                ToolsHome(navigate)
            }
            composable<Route.Tools.Hash> {
                HashTool()
            }
            composable<Route.Tools.PGPEncrypt> {
                PGPEncryptTool()
            }
            composable<Route.Tools.PGPDecrypt> {
                PGPDecryptTool()
            }
            composable<Route.Tools.QRScanner> {
                QRScannerTool()
            }
            composable<Route.Tools.Git> {
                GitTool()
            }
            composable<Route.Tools.Biometrics> {
                BiometricAuthTool()
            }
        }
    }
}

@Composable
fun ToolsHome(navigate: (route: Any) -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        toolsRoutes.forEach {
            ListItem(headlineContent = {
                Text(text = it.name)
            }, modifier = Modifier.clickable(onClick = { navigate(it.route) }))
        }
    }
}
