@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package dev.xorkevin.multitool

import android.app.Application
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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlin.io.path.nameWithoutExtension

@Composable
fun PassManager(toggleNavDrawer: () -> Unit) = ViewModelScope(GitRepoManagerViewModel::class) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val navigate: (route: Any) -> Unit = remember(navController) {
        { route -> navController.navigate(route = route) }
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
                        if (backStackEntry.destination.hasRoute(Route.PassManager.Repo::class)) {
                            val route = backStackEntry.toRoute<Route.PassManager.Repo>()
                            if (route.dir == "") {
                                route.name
                            } else {
                                Paths.get(route.dir).name
                            }
                        } else if (backStackEntry.destination.hasRoute(Route.PassManager.RepoEntry::class)) {
                            val route = backStackEntry.toRoute<Route.PassManager.RepoEntry>()
                            if (route.path == "") {
                                route.name
                            } else {
                                Paths.get(route.path).nameWithoutExtension
                            }
                        } else {
                            "Password Store"
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
                PassManagerRepo(route.name, route.dir, navigate)
            }
            composable<Route.PassManager.RepoEntry> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.PassManager.RepoEntry>()
                PassManagerRepoEntry(route.name, route.path)
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
                    Text(
                        text = if (it.isDir) {
                            Paths.get(it.path).name
                        } else {
                            val p = Paths.get(it.path)
                            if (p.nameWithoutExtension == "") {
                                p.name
                            } else {
                                p.nameWithoutExtension
                            }
                        }
                    )
                },
                trailingContent = {
                    Box(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        if (it.isDir) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View repo"
                            )
                        }
                    }
                },
                modifier = Modifier.clickable(onClick = {
                    if (it.isDir) {
                        navigate(Route.PassManager.Repo(repoName, it.path))
                    } else {
                        navigate(Route.PassManager.RepoEntry(repoName, it.path))
                    }
                }),
            )
        }
    }
}

@Composable
fun PassManagerRepoEntry(repoName: String, repoPath: String) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            PassManagerRepoEntryContents(repoName, repoPath)
        }
    }
}

@Composable
fun PassManagerRepoEntryContents(repoName: String, repoPath: String) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    LaunchedEffect(repoName, repoPath) {
        passManagerViewModel.setRepoEntry(repoName, repoPath)
    }

    val repoEntry by passManagerViewModel.repoEntry.collectAsStateWithLifecycle()
    repoEntry.onFailure {
        Text(
            text = "Failed to get repo entry: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    repoEntry.onSuccess { contents ->
        var showPass by remember { mutableStateOf(false) }
        TextField(
            value = contents.password,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Password") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showPass) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showPass = !showPass },
                    modifier = Modifier.padding(8.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock, contentDescription = "Show pass"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        var showOTP by remember { mutableStateOf(false) }
        TextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "OTP") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showOTP) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showOTP = !showOTP },
                    modifier = Modifier.padding(8.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock, contentDescription = "Show otp"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        var showRawData by remember { mutableStateOf(false) }
        TextField(
            value = contents.rawData,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Raw contents") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showRawData) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showRawData = !showRawData },
                    modifier = Modifier.padding(8.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock, contentDescription = "Show raw contents"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
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

    private data class RepoLocation(val name: String, val dir: String)

    private val _repoLocation = MutableViewModelStateFlow(RepoLocation("", ""))
    val repoContents = _repoLocation.flow.mapLatest {
        if (it.name == "") {
            Result.success(listOf())
        } else {
            gitRepoService.listRepoContents(it.name, it.dir)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Result.success(listOf()))

    fun setRepoLocation(name: String, dir: String) {
        _repoLocation.update { RepoLocation(name, dir) }
    }

    private data class RepoEntry(val name: String, val path: String)
    data class RepoEntryContents(val rawData: String, val password: String, val otpUri: String)

    private val _repoEntry = MutableViewModelStateFlow(RepoEntry("", ""))
    val repoEntry = _repoEntry.flow.mapLatest {
        if (it.name == "") {
            Result.success(RepoEntryContents(rawData = "", password = "", otpUri = ""))
        } else {
            Result.success(
                RepoEntryContents(
                    rawData = "hello\nworld\n", password = "sample password", otpUri = ""
                )
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        Result.success(RepoEntryContents(rawData = "", password = "", otpUri = ""))
    )

    fun setRepoEntry(name: String, path: String) {
        _repoEntry.update { RepoEntry(name, path) }
    }

    companion object : ScopedViewModelFactory<PassManagerViewModel> {
        override fun create(app: Application): PassManagerViewModel {
            app as MainApplication
            return PassManagerViewModel(app.container.gitRepoService, app.container.keyStore)
        }
    }
}
