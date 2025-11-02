@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package dev.xorkevin.multitool

import android.app.Application
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.file.Paths
import kotlin.io.path.name

@Composable
fun PassManager(toggleNavDrawer: () -> Unit) = ViewModelScope(GitRepoManagerViewModel::class) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val navigate: (route: Any) -> Unit = remember(navController) {
        { route ->
            navController.navigate(route = route)
        }
    }

    val showSnackbar: suspend (msg: String) -> Unit = remember(snackbarHostState) {
        { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = currentBackStackEntry?.let { backStackEntry ->
                        if (!backStackEntry.destination.hasRoute(Route.PassManager.Repo::class)) {
                            "Password Store"
                        } else {
                            val route = backStackEntry.toRoute<Route.PassManager.Repo>()
                            val name = Paths.get(route.dir).name
                            if (name == "") {
                                "/"
                            } else {
                                name
                            }
                        }
                    } ?: "Password Store")
                },
                navigationIcon = {
                    if (currentBackStackEntry?.destination?.hasRoute(Route.PassManager.Home::class)
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.PassManager.Home,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            composable<Route.PassManager.Home> {
                PassManagerHome(navigate)
            }
            composable<Route.PassManager.Repo> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.PassManager.Repo>()
                Log.i("KEVIN", "route update ${route.name}, ${route.dir}")
                PassManagerRepo(route.name, route.dir, navigate)
            }
        }
    }
}

@Composable
fun PassManagerHome(navigate: (route: Any) -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            PassManagerRepoList(navigate)
        }
    }
}

@Composable
fun PassManagerRepoList(navigate: (route: Any) -> Unit) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        passManagerViewModel.refreshGitRepos()
    }

    val gitRepos by passManagerViewModel.gitRepos.collectAsStateWithLifecycle()

    gitRepos.onFailure {
        Text(
            text = "Failed to get repos: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    gitRepos.onSuccess { repos ->
        Text(
            text = "${repos.size} Repos",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        repos.forEach {
            ListItem(
                headlineContent = {
                    Text(text = it.name)
                },
                trailingContent = {
                    Box(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View repo"
                        )
                    }
                },
                modifier = Modifier.clickable(onClick = {
                    navigate(
                        Route.PassManager.Repo(
                            it.name, ""
                        )
                    )
                }),
            )
        }
    }
}

@Composable
fun PassManagerRepo(repoName: String, repoDir: String, navigate: (route: Any) -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            PassManagerRepoContents(repoName, repoDir, navigate)
        }
    }
}

@Composable
fun PassManagerRepoContents(repoName: String, repoDir: String, navigate: (route: Any) -> Unit) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    LaunchedEffect(repoName, repoDir) {
        Log.i("KEVIN", "set location ${repoName}, ${repoDir}")
        passManagerViewModel.setRepoLocation(repoName, repoDir)
    }

    val repoContents by passManagerViewModel.repoContents.collectAsStateWithLifecycle()
    repoContents.onFailure {
        Text(
            text = "Failed to get repo contents: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    repoContents.onSuccess { contents ->
        contents.forEach {
            ListItem(
                headlineContent = {
                    Text(text = Paths.get(it.path).name)
                },
                trailingContent = {
                    Box(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View repo"
                        )
                    }
                },
                modifier = Modifier.clickable(onClick = {
                    if (it.isDir) {
                        Log.i("KEVIN", "navigate to ${repoName}, ${it.path}")
                        navigate(
                            Route.PassManager.Repo(
                                repoName, it.path,
                            )
                        )
                    }
                }),
            )
        }
    }
}

class PassManagerViewModel(
    private val gitRepoService: GitRepoService, private val keyStore: KeyStoreService
) : ViewModel() {
    private val _gitRepos =
        MutableViewModelStateFlow(Result.success(listOf<GitRepoService.GitRepo>()))
    val gitRepos = _gitRepos.flow

    fun refreshGitRepos() {
        viewModelScope.launch {
            run {
                val res = gitRepoService.getAllRepos()
                _gitRepos.update { res }
            }
        }
    }

    data class RepoLocation(val name: String, val dir: String)

    private val _repoLocation = MutableViewModelStateFlow(RepoLocation("", ""))
    val repoContents = _repoLocation.flow.mapLatest {
        if (it.name == "") {
            Result.success(listOf())
        } else {
            Log.i("KEVIN", "get contents ${it.name}, ${it.dir}")
            gitRepoService.listRepoContents(it.name, it.dir)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Result.success(listOf()))

    fun setRepoLocation(name: String, dir: String) {
        _repoLocation.update { RepoLocation(name, dir) }
    }

    companion object : ScopedViewModelFactory<PassManagerViewModel> {
        override fun create(app: Application): PassManagerViewModel {
            app as MainApplication
            return PassManagerViewModel(app.container.gitRepoService, app.container.keyStore)
        }
    }
}
