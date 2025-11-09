@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalPermissionsApi::class,
    FlowPreview::class
)

package dev.xorkevin.multitool

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.codec.binary.Base32
import org.apache.http.client.utils.URLEncodedUtils
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@Composable
fun PassManager(toggleNavDrawer: () -> Unit) = ViewModelScope(PassManagerViewModel::class) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val navigate: (route: Any) -> Unit = remember(navController) {
        { route -> navController.navigate(route = route) }
    }

    val showSnackbar: suspend (msg: String) -> Unit = remember(snackbarHostState) {
        { msg -> snackbarHostState.showSnackbar(msg) }
    }

    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    LaunchedEffect(showSnackbar) {
        passManagerViewModel.snackEvents.collectLatest {
            showSnackbar(it)
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
                actions = {
                    currentBackStackEntry?.let { backStackEntry ->
                        if (backStackEntry.destination.hasRoute(Route.PassManager.Repo::class)) {
                            val route = backStackEntry.toRoute<Route.PassManager.Repo>()
                            PassManagerRepoDropdownMenu(route.name, route.dir)
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
fun PassManagerRepo(
    repoName: String,
    repoDir: String,
    navigate: (route: Any) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            PassManagerRepoContents(repoName, repoDir, navigate)
        }
    }
}

@Composable
fun PassManagerRepoContents(
    repoName: String,
    repoDir: String,
    navigate: (route: Any) -> Unit,
) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    var repoContentSearch by passManagerViewModel.repoContentSearch.collectAsStateWithLifecycle()
    LaunchedEffect(repoName) {
        repoContentSearch = ""
        passManagerViewModel.setRepo(repoName)
    }
    LaunchedEffect(repoName, repoDir) {
        passManagerViewModel.setRepoLocation(repoName, repoDir)
    }

    TextField(
        value = repoContentSearch,
        onValueChange = { repoContentSearch = it },
        label = { Text(text = "Search") },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                Modifier.padding(8.dp, 8.dp)
            )
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )

    val repoContentSearchResults by passManagerViewModel.repoContentSearchResults.collectAsStateWithLifecycle()
    val repoContents by passManagerViewModel.repoContents.collectAsStateWithLifecycle()

    if (repoContentSearch != "") {
        repoContentSearchResults.onFailure {
            Text(
                text = "Failed to get repo contents: ${it.toString()}",
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
        repoContentSearchResults.onSuccess { contents ->
            contents.forEach {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "${it.first}, ${it.second}"
                        )
                    },
                    modifier = Modifier.clickable(onClick = {
                        navigate(Route.PassManager.RepoEntry(repoName, it.first))
                    }),
                )
            }
            if (contents.isEmpty()) {
                Text(
                    text = "No results", modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth()
                )
            }
        }
    } else {
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
                                    contentDescription = "View directory"
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
            if (contents.isEmpty()) {
                Text(
                    text = "Empty dir", modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PassManagerRepoDropdownMenu(name: String, dir: String) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Pass manager entry options")
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(text = { Text("Pull repo") }, onClick = {
                expanded = false
                passManagerViewModel.pullGitRepo(name, dir)
            })
        }
    }
}

@Composable
fun PassManagerRepoEntry(repoName: String, repoPath: String) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            NotificationPermission {
                PassManagerRepoEntryContents(repoName, repoPath)
            }
        }
    }
}

@Composable
fun PassManagerRepoEntryContents(repoName: String, repoPath: String) {
    val passManagerViewModel: PassManagerViewModel = scopedViewModel()

    LaunchedEffect(repoName, repoPath) {
        passManagerViewModel.setRepoEntry(repoName, repoPath)
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

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
        Text(
            text = repoPath, modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        var showPass by remember { mutableStateOf(false) }
        TextField(
            value = if (showPass) {
                contents.password
            } else {
                "------------"
            },
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
                Row {
                    IconButton(
                        onClick = { showPass = !showPass },
                        modifier = Modifier.padding(8.dp, 8.dp),
                    ) {
                        Icon(
                            imageVector = if (showPass) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            }, contentDescription = "Show pass"
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData(
                                            ClipDescription(
                                                "multitool-password-store-pw",
                                                arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                                            ).apply {
                                                extras = PersistableBundle().apply {
                                                    putBoolean(
                                                        ClipDescription.EXTRA_IS_SENSITIVE, true
                                                    )
                                                }
                                            },
                                            ClipData.Item(contents.password),
                                        )
                                    )
                                )
                                enqueueClipboardClear(context)
                            }
                        },
                        modifier = Modifier.padding(8.dp, 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy pass"
                        )
                    }
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        if (contents.totpUri != null) {
            var showOTP by remember { mutableStateOf(false) }
            var otp by remember { mutableStateOf("") }
            var otpProgress by remember { mutableStateOf(0L to 1f) }
            LaunchedEffect(contents.totpUri) {
                val totp = contents.totpUri
                val periodMSFloat = totp.period * 1000f
                val periodMS = totp.period * 1000
                while (true) {
                    val nowMS = System.currentTimeMillis()
                    val nowS = nowMS / 1000
                    val remainderMS = periodMS - nowMS.mod(periodMS)
                    val remainderS = remainderMS / 1000
                    otpProgress = remainderS to remainderMS / periodMSFloat
                    otp = CryptoUtil.generateTOTP(
                        totp.secret, nowS, totp.period, totp.alg, totp.digits
                    )
                    delay(250.milliseconds)
                }
            }
            TextField(
                value = if (otp != "") {
                    otp
                } else {
                    "------"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text(text = "OTP") },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center
                ),
                visualTransformation = if (showOTP) {
                    OTPVisualTransformation()
                } else {
                    OTPHiddenVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { showOTP = !showOTP },
                        modifier = Modifier.padding(8.dp, 8.dp),
                    ) {
                        Icon(
                            imageVector = if (showOTP) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            }, contentDescription = "Show otp"
                        )
                    }
                },
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "${otpProgress.first}s".padStart(3, ' '),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp, 0.dp)
                )
                val progress by animateFloatAsState(
                    targetValue = otpProgress.second,
                    animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    label = "remaining totp time"
                )
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier
                        .padding(8.dp, 0.dp)
                        .weight(1f)
                )
            }
            if (contents.totpUri.accountName != "") {
                Text(
                    text = "Account: ${contents.totpUri.accountName}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth()
                )
            }
            if (contents.totpUri.issuer != "") {
                Text(
                    text = "Issuer: ${contents.totpUri.issuer}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth()
                )
            }
        }
        var showAdditionalData by remember { mutableStateOf(false) }
        TextField(
            value = if (showAdditionalData) {
                contents.additionalData
            } else {
                "------------"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Additional data") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showAdditionalData) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showAdditionalData = !showAdditionalData },
                    modifier = Modifier.padding(8.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = if (showAdditionalData) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        }, contentDescription = "Show additional data"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        var showTOTPSecret by remember { mutableStateOf(false) }
        TextField(
            value = if (showTOTPSecret) {
                contents.totpSecret
            } else {
                "------------"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "TOTP Secret") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showTOTPSecret) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { showTOTPSecret = !showTOTPSecret },
                    modifier = Modifier.padding(8.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = if (showTOTPSecret) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        }, contentDescription = "Show TOTP secret"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
fun NotificationPermission(content: @Composable () -> Unit) {
    val notifPermissionState =
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    if (notifPermissionState.status.isGranted) {
        content()
    } else {
        if (notifPermissionState.status.shouldShowRationale) {
            Text(
                text = "Notification permission is needed to clear the clipboard",
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
        Button(
            onClick = { notifPermissionState.launchPermissionRequest() },
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Grant notification permission")
        }
    }
}

class OTPVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformedText = when (text.length) {
            8 -> AnnotatedString("${text.text.substring(0, 4)} ${text.text.substring(4, 8)}")
            6 -> AnnotatedString("${text.text.substring(0, 3)} ${text.text.substring(3, 6)}")
            else -> text
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when (text.length) {
                8 -> if (offset > 3) {
                    offset + 1
                } else {
                    offset
                }

                6 -> if (offset > 2) {
                    offset + 1
                } else {
                    offset
                }

                else -> offset
            }

            override fun transformedToOriginal(offset: Int): Int = when (text.length) {
                8 -> if (offset > 4) {
                    offset - 1
                } else {
                    offset
                }

                6 -> if (offset > 3) {
                    offset - 1
                } else {
                    offset
                }

                else -> offset
            }
        }
        return TransformedText(transformedText, offsetMapping)
    }
}

class OTPHiddenVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = "\u2022".repeat(text.length)
        val transformedText = when (text.length) {
            8 -> AnnotatedString("${t.substring(0, 4)} ${t.substring(4, 8)}")
            6 -> AnnotatedString("${t.substring(0, 3)} ${t.substring(3, 6)}")
            else -> AnnotatedString(t)
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when (text.length) {
                8 -> if (offset > 3) {
                    offset + 1
                } else {
                    offset
                }

                6 -> if (offset > 2) {
                    offset + 1
                } else {
                    offset
                }

                else -> offset
            }

            override fun transformedToOriginal(offset: Int): Int = when (text.length) {
                8 -> if (offset > 4) {
                    offset - 1
                } else {
                    offset
                }

                6 -> if (offset > 3) {
                    offset - 1
                } else {
                    offset
                }

                else -> offset
            }
        }
        return TransformedText(transformedText, offsetMapping)
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

    private val _snackEvents = MutableSharedFlow<String>()
    val snackEvents = _snackEvents.asSharedFlow()

    private val allRepoContents = MutableViewModelStateFlow(Result.success(listOf<String>()))

    fun setRepo(name: String) {
        viewModelScope.launch {
            if (name == "") {
                allRepoContents.update { Result.success(listOf()) }
            } else {
                val res = gitRepoService.listAllRepoContents(name)
                allRepoContents.update { res }
            }
        }
    }

    val repoContentSearch = MutableViewModelStateFlow("")
    val repoContentSearchResults = combine(
        allRepoContents.flow, repoContentSearch.flow.debounce(125.milliseconds)
    ) { a, b -> a to b }.mapLatest { (contentsResult, search) ->
        if (search == "") {
            return@mapLatest Result.success(listOf())
        }
        val contents = contentsResult.getOrElse { return@mapLatest Result.failure(it) }
        delay(125.milliseconds)
        withContext(Dispatchers.Default) {
            Result.success(
                contents.asSequence()
                    .map { it to StringSearchUtil.searchSmithWaterman(it, search) }
                    .filter { it.second > 0 }.sortedByDescending { it.second }.toList()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Result.success(listOf()))

    private val _repoContents =
        MutableViewModelStateFlow(Result.success(listOf<GitRepoService.RepoFile>()))
    val repoContents = _repoContents.flow


    fun setRepoLocation(name: String, dir: String) {
        viewModelScope.launch {
            if (name == "") {
                _repoContents.update { Result.success(listOf()) }
            } else {
                val res = gitRepoService.listRepoContents(name, dir)
                _repoContents.update { res }
            }
        }
    }

    fun pullGitRepo(name: String, dir: String) {
        viewModelScope.launch {
            _snackEvents.emit("Pulling repo $name")
            val res = gitRepoService.pullRepo(name)
            res.onFailure {
                _snackEvents.emit("Failed pulling repo $name: ${it.toString()}")
            }
            res.onSuccess {
                _snackEvents.emit("Pulled repo $name")
                setRepoLocation(name, dir)
            }
        }
    }

    private data class RepoEntry(val name: String, val path: String)
    data class RepoEntryContents(
        val additionalData: String,
        val password: String,
        val totpUri: TOTPUri?,
        val totpSecret: String
    )

    private val _repoEntry = MutableViewModelStateFlow(RepoEntry("", ""))
    val repoEntry = _repoEntry.flow.mapLatest { entry ->
        if (entry.name == "") {
            return@mapLatest Result.success(
                RepoEntryContents(
                    additionalData = "", password = "", totpUri = null, totpSecret = ""
                )
            )
        }
        val repo =
            gitRepoService.getRepo(entry.name).getOrElse { return@mapLatest Result.failure(it) }
        val encData = gitRepoService.getRepoEntryContent(entry.name, entry.path)
            .getOrElse { return@mapLatest Result.failure(it) }
        val data = keyStore.gpgDecrypt(repo.gpgKeyName, encData)
            .getOrElse { return@mapLatest Result.failure(it) }
        val strData = data.decodeToString().lines()
        val totpUri = strData.firstNotNullOfOrNull { parseTOTPUri(it) }
        val additionalData = strData.subList(1, strData.size).filter { !totpUriPattern.matches(it) }
            .joinToString(separator = "\n", postfix = "\n")
        val totpSecret = strData.subList(1, strData.size).filter { totpUriPattern.matches(it) }
            .joinToString(separator = "\n", postfix = "\n")
        return@mapLatest Result.success(
            RepoEntryContents(
                additionalData = additionalData,
                password = strData.firstOrNull() ?: "", totpUri = totpUri, totpSecret = totpSecret,
            ),
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(), Result.success(
            RepoEntryContents(
                additionalData = "", password = "", totpUri = null, totpSecret = ""
            )
        )
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

suspend fun enqueueClipboardClear(context: Context) {
    val manager = WorkManager.getInstance(context)
    val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>().run {
        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        build()
    }
    manager.enqueueUniqueWork("password-store-clipboard-clear", ExistingWorkPolicy.REPLACE, request)
        .await()
}

internal const val clipboardNotificationId = 1

class ClipboardClearWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    private val clipboardNotificationChannelId = "password-store-clipboard-clear"

    override suspend fun doWork(): Result {
        createNotificationChannel()
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.notify(clipboardNotificationId, createNotification())
        try {
            delay(30.seconds)
        } catch (_: CancellationException) {
        } finally {
            clearClipboard(applicationContext)
        }
        return Result.success()
    }

    private fun createNotification(): Notification {
        val manager = WorkManager.getInstance(applicationContext)
        val pendingIntent = manager.createCancelPendingIntent(id)
        return Notification.Builder(applicationContext, clipboardNotificationChannelId).run {
            setContentTitle("Password store clipboard")
            setContentText("Clear clipboard")
            setSmallIcon(R.drawable.ic_launcher_foreground)
            setContentIntent(pendingIntent)
            setDeleteIntent(pendingIntent)
            setUsesChronometer(true)
            setChronometerCountDown(true)
            setShowWhen(true)
            setWhen(System.currentTimeMillis() + 30_000)
            setAutoCancel(true)
            build()
        }
    }

    private fun createNotificationChannel() {
        val notifications = applicationContext.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                clipboardNotificationChannelId,
                "Password store clipboard clear",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }
}

internal fun clearClipboard(appContext: Context) {
    val clipboard = appContext.getSystemService(ClipboardManager::class.java)
    clipboard.clearPrimaryClip()
    val notifications = appContext.getSystemService(NotificationManager::class.java)
    notifications.cancel(clipboardNotificationId)
}

class TOTPUri(
    val secret: ByteArray,
    val period: Long,
    val alg: String,
    val digits: Int,
    val accountName: String,
    val issuer: String
)

private val totpUriPattern = Regex("^otpauth://totp/.*")
private val totpUriPathSplitPattern = Regex(": *")
private val totpAllowedAlgs = setOf("SHA1", "SHA256", "SHA512")
private val totpAllowedDigits = setOf("6", "8")

internal fun parseTOTPUri(s: String): TOTPUri? {
    if (!totpUriPattern.matches(s)) {
        return null
    }
    val uri = try {
        URI(s)
    } catch (_: URISyntaxException) {
        return null
    }
    if (uri.scheme != "otpauth") {
        return null
    }
    if (uri.host != "totp") {
        return null
    }
    val q = try {
        URLEncodedUtils.parse(uri.query, StandardCharsets.UTF_8)
    } catch (_: Exception) {
        return null
    }
    val secretStr = q.firstNotNullOfOrNull {
        if (it.name == "secret") {
            it.value
        } else {
            null
        }
    }
    if (secretStr == null) {
        return null
    }
    val secret = try {
        Base32().decode(secretStr)
    } catch (_: Exception) {
        return null
    }
    val algStr = q.firstNotNullOfOrNull {
        if (it.name == "algorithm") {
            it.value
        } else {
            null
        }
    } ?: "SHA1"
    if (!totpAllowedAlgs.contains(algStr)) {
        return null
    }
    val digitsStr = q.firstNotNullOfOrNull {
        if (it.name == "digits") {
            it.value
        } else {
            null
        }
    } ?: "6"
    if (!totpAllowedDigits.contains(digitsStr)) {
        return null
    }
    val digits = digitsStr.toIntOrNull() ?: return null
    val periodStr = q.firstNotNullOfOrNull {
        if (it.name == "period") {
            it.value
        } else {
            null
        }
    } ?: "30"
    val period = periodStr.toLongOrNull()
    if (period == null || period < 1) {
        return null
    }
    val pathSplit = uri.path.removePrefix("/").split(totpUriPathSplitPattern, 2)
    val (accountName, issuer) = if (pathSplit.size == 2) {
        pathSplit[1] to pathSplit[0]
    } else {
        pathSplit[0] to (q.firstNotNullOfOrNull {
            if (it.name == "issuer") {
                it.value
            } else {
                null
            }
        } ?: "")
    }
    return TOTPUri(
        secret = secret,
        period = period,
        alg = algStr,
        digits = digits,
        accountName = accountName,
        issuer = issuer,
    )
}
