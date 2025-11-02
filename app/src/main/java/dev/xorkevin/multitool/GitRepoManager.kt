@file:OptIn(ExperimentalMaterial3Api::class)

package dev.xorkevin.multitool

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun GitRepoManager(showSnackbar: suspend (msg: String) -> Unit) =
    ViewModelScope(GitRepoManagerViewModel::class) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            VaultUnlocker {
                GitRepoManagerInput()
                GitRepoManagerList(showSnackbar)
            }
        }
    }

@Composable
fun GitRepoManagerInput() {
    val gitRepoManagerViewModel: GitRepoManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        gitRepoManagerViewModel.refreshGitRepos()
    }

    var name by gitRepoManagerViewModel.gitRepoName.collectAsStateWithLifecycle()
    var url by gitRepoManagerViewModel.gitRepoUrl.collectAsStateWithLifecycle()
    var branch by gitRepoManagerViewModel.gitRepoBranch.collectAsStateWithLifecycle()
    var sshKeyName by gitRepoManagerViewModel.gitRepoSshKeyName.collectAsStateWithLifecycle()
    val addRes by gitRepoManagerViewModel.addGitRepoRes.collectAsStateWithLifecycle()

    val sshKeys by gitRepoManagerViewModel.sshKeys.collectAsStateWithLifecycle()

    Text(
        text = "Add a repo",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    )
    TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(text = "Name") },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(text = "Url") },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { url = (it ?: "").trim() },
                modifier = Modifier.padding(8.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan url"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = branch,
        onValueChange = { branch = it },
        label = { Text(text = "Branch") },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { branch = (it ?: "").trim() },
                modifier = Modifier.padding(8.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan branch"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    DropdownFormField(
        options = sshKeys.getOrDefault(listOf()).map { it.name },
        value = sshKeyName,
        onValueChange = { sshKeyName = it },
        label = { Text(text = "SSH key name") },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    Button(
        onClick = { gitRepoManagerViewModel.addGitRepo() },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Add repo")
    }
    addRes.onFailure {
        Text(
            text = "Failed to add git repo: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun GitRepoManagerList(showSnackbar: suspend (msg: String) -> Unit) {
    val gitRepoManagerViewModel: GitRepoManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        gitRepoManagerViewModel.refreshGitRepos()
    }

    LaunchedEffect(Unit) {
        gitRepoManagerViewModel.snackEvents.collectLatest {
            showSnackbar(it)
        }
    }

    val gitRepos by gitRepoManagerViewModel.gitRepos.collectAsStateWithLifecycle()
    val cloneGitRepoRes by gitRepoManagerViewModel.cloneGitRepoRes.collectAsStateWithLifecycle()
    val displayDeleteGitRepoModal by gitRepoManagerViewModel.displayDeleteGitRepoModal.collectAsStateWithLifecycle()
    val displayDeleteGitRepoDirModal by gitRepoManagerViewModel.displayDeleteGitRepoDirModal.collectAsStateWithLifecycle()

    TextButton(
        onClick = { gitRepoManagerViewModel.refreshGitRepos() },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Refresh")
    }
    gitRepos.onFailure {
        Text(
            text = "Failed to get repos: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    cloneGitRepoRes.onFailure {
        Text(
            text = "Failed to clone git repo: ${it.toString()}",
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
            ListItem(headlineContent = {
                Text(text = it.name)
            }, supportingContent = {
                Column {
                    Text(text = "URL: ${it.url}")
                    Text(text = "Branch: ${it.branch}")
                    Text(text = "SSH key: ${it.sshKeyName}")
                }
            }, trailingContent = {
                GitRepoManagerDropdownMenu(it.name)
            })
        }
    }
    if (displayDeleteGitRepoModal) {
        val candidateGitRepoDeleteName by gitRepoManagerViewModel.candidateGitRepoDeleteName.collectAsStateWithLifecycle()
        val deleteRes by gitRepoManagerViewModel.deleteGitRepoRes.collectAsStateWithLifecycle()

        AlertDialog(title = { Text(text = "Delete git repo") }, text = {
            Column {
                Text(text = "This will delete the git repo \"$candidateGitRepoDeleteName\", both its configuration and its cloned data.")
                deleteRes.onFailure {
                    Text(
                        text = "Failed to delete git repo: ${it.toString()}",
                        modifier = Modifier
                            .padding(16.dp, 8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }, onDismissRequest = { gitRepoManagerViewModel.dismissDeleteGitRepo() }, confirmButton = {
            TextButton(
                onClick = {
                    gitRepoManagerViewModel.deleteGitRepo(candidateGitRepoDeleteName)
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { gitRepoManagerViewModel.dismissDeleteGitRepo() }) {
                Text("Cancel")
            }
        })
    }
    if (displayDeleteGitRepoDirModal) {
        val candidateGitRepoDirDeleteName by gitRepoManagerViewModel.candidateGitRepoDirDeleteName.collectAsStateWithLifecycle()
        val deleteDirRes by gitRepoManagerViewModel.deleteGitRepoDirRes.collectAsStateWithLifecycle()

        AlertDialog(
            title = { Text(text = "Delete git repo dir") },
            text = {
                Column {
                    Text(text = "This will delete the git repo dir \"$candidateGitRepoDirDeleteName\", but not its configuration.")
                    deleteDirRes.onFailure {
                        Text(
                            text = "Failed to delete git repo dir: ${it.toString()}",
                            modifier = Modifier
                                .padding(16.dp, 8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            },
            onDismissRequest = { gitRepoManagerViewModel.dismissDeleteGitRepoDir() },
            confirmButton = {
                TextButton(
                    onClick = {
                        gitRepoManagerViewModel.deleteGitRepoDir(candidateGitRepoDirDeleteName)
                    }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { gitRepoManagerViewModel.dismissDeleteGitRepoDir() }) {
                    Text("Cancel")
                }
            })
    }
}

@Composable
fun GitRepoManagerDropdownMenu(name: String) {
    val gitRepoManagerViewModel: GitRepoManagerViewModel = scopedViewModel()

    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Git repo options")
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(text = { Text("Clone") }, onClick = {
                expanded = false
                gitRepoManagerViewModel.cloneGitRepo(name)
            })
            DropdownMenuItem(text = { Text("Delete dir") }, onClick = {
                expanded = false
                gitRepoManagerViewModel.promptDeleteGitRepoDir(name)
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                expanded = false
                gitRepoManagerViewModel.promptDeleteGitRepo(name)
            })
        }
    }
}

@Composable
fun DropdownFormField(
    options: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = label,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onValueChange(option)
                    expanded = false
                })
            }
        }
    }
}

class GitRepoManagerViewModel(
    private val gitRepoService: GitRepoService, private val keyStore: KeyStoreService
) : ViewModel() {
    private val _gitRepos =
        MutableViewModelStateFlow(Result.success(listOf<GitRepoService.GitRepo>()))
    val gitRepos = _gitRepos.flow
    private val _sshKeys =
        MutableViewModelStateFlow(Result.success(listOf<KeyStoreService.SshKeyNameTuple>()))
    val sshKeys = _sshKeys.flow

    fun refreshGitRepos() {
        viewModelScope.launch {
            run {
                val res = gitRepoService.getAllRepos()
                _gitRepos.update { res }
            }
            run {
                val res = keyStore.getAllSshKeys()
                _sshKeys.update { res }
            }
        }
    }

    private val _snackEvents = MutableSharedFlow<String>()
    val snackEvents = _snackEvents.asSharedFlow()

    private val _candidateGitRepoDeleteName = MutableViewModelStateFlow("")
    val candidateGitRepoDeleteName = _candidateGitRepoDeleteName.flow
    private val _displayDeleteGitRepoModal = MutableViewModelStateFlow(false)
    val displayDeleteGitRepoModal = _displayDeleteGitRepoModal.flow
    fun promptDeleteGitRepo(name: String) {
        _candidateGitRepoDeleteName.update { name }
        _displayDeleteGitRepoModal.update { true }
    }

    fun dismissDeleteGitRepo() {
        _displayDeleteGitRepoModal.update { false }
        _deleteGitRepoRes.update { Result.success(Unit) }
        _candidateGitRepoDeleteName.update { "" }
    }

    private val _deleteGitRepoRes = MutableViewModelStateFlow(Result.success(Unit))
    val deleteGitRepoRes = _deleteGitRepoRes.flow

    fun deleteGitRepo(name: String) {
        viewModelScope.launch {
            val res = gitRepoService.rmRepo(name)
            _deleteGitRepoRes.update { res }
            res.onSuccess {
                dismissDeleteGitRepo()
                refreshGitRepos()
            }
        }
    }

    private val _candidateGitRepoDirDeleteName = MutableViewModelStateFlow("")
    val candidateGitRepoDirDeleteName = _candidateGitRepoDirDeleteName.flow
    private val _displayDeleteGitRepoDirModal = MutableViewModelStateFlow(false)
    val displayDeleteGitRepoDirModal = _displayDeleteGitRepoDirModal.flow

    fun promptDeleteGitRepoDir(name: String) {
        _candidateGitRepoDirDeleteName.update { name }
        _displayDeleteGitRepoDirModal.update { true }
    }

    fun dismissDeleteGitRepoDir() {
        _displayDeleteGitRepoDirModal.update { false }
        _deleteGitRepoDirRes.update { Result.success(Unit) }
        _candidateGitRepoDirDeleteName.update { "" }
    }

    private val _deleteGitRepoDirRes = MutableViewModelStateFlow(Result.success(Unit))
    val deleteGitRepoDirRes = _deleteGitRepoDirRes.flow

    fun deleteGitRepoDir(name: String) {
        viewModelScope.launch {
            val res = gitRepoService.rmRepoDir(name)
            _deleteGitRepoRes.update { res }
            res.onSuccess {
                dismissDeleteGitRepoDir()
                _snackEvents.emit("Deleted repo dir $name")
            }
        }
    }

    private val _cloneGitRepoRes = MutableViewModelStateFlow(Result.success(Unit))
    val cloneGitRepoRes = _cloneGitRepoRes.flow

    fun cloneGitRepo(name: String) {
        viewModelScope.launch {
            val res = gitRepoService.cloneRepo(name)
            _cloneGitRepoRes.update { res }
            res.onSuccess {
                _snackEvents.emit("Cloned repo $name")
            }
        }
    }

    val gitRepoName = MutableViewModelStateFlow("")
    val gitRepoUrl = MutableViewModelStateFlow("")
    val gitRepoBranch = MutableViewModelStateFlow("")
    val gitRepoSshKeyName = MutableViewModelStateFlow("")

    private val _addGitRepoRes = MutableViewModelStateFlow(Result.success(Unit))
    val addGitRepoRes = _addGitRepoRes.flow

    fun addGitRepo() {
        viewModelScope.launch {
            val name = gitRepoName.value.trim()
            val url = gitRepoUrl.value.trim()
            val branch = gitRepoBranch.value.trim()
            val sshKeyName = gitRepoSshKeyName.value.trim()
            if (name.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Name may not be empty")) }
                return@launch
            }
            if (url.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Url may not be empty")) }
                return@launch
            }
            if (branch.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Branch may not be empty")) }
                return@launch
            }
            if (sshKeyName.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Ssh key may not be empty")) }
                return@launch
            }

            val res = gitRepoService.addRepos(
                GitRepoService.GitRepo(
                    name = name,
                    url = url,
                    branch = branch,
                    sshKeyName = sshKeyName,
                )
            )
            _addGitRepoRes.update { res }
            res.onSuccess {
                gitRepoName.update { "" }
                gitRepoUrl.update { "" }
                gitRepoBranch.update { "" }
                gitRepoSshKeyName.update { "" }
                refreshGitRepos()
            }
        }
    }

    companion object : ScopedViewModelFactory<GitRepoManagerViewModel> {
        override fun create(app: Application): GitRepoManagerViewModel {
            app as MainApplication
            return GitRepoManagerViewModel(app.container.gitRepoService, app.container.keyStore)
        }
    }
}
