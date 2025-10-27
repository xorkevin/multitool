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
import kotlinx.coroutines.launch

@Composable
fun GitRepoManager() = ViewModelScope(GitRepoManagerViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        GitRepoManagerInput()
        GitRepoManagerList()
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
                modifier = Modifier.padding(16.dp, 8.dp),
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
fun GitRepoManagerList() {
    val gitRepoManagerViewModel: GitRepoManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        gitRepoManagerViewModel.refreshGitRepos()
    }

    val gitRepos by gitRepoManagerViewModel.gitRepos.collectAsStateWithLifecycle()
    val displayDeleteGitRepoModal by gitRepoManagerViewModel.displayDeleteGitRepoModal.collectAsStateWithLifecycle()

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
    gitRepos.onSuccess { keys ->
        Text(
            text = "${keys.size} Repos",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        keys.forEach {
            ListItem(headlineContent = {
                Text(text = it.name)
            }, supportingContent = {
                Column {
                    Text(text = "URL: ${it.url}")
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
            expanded = expanded, onDismissRequest = { expanded = false }) {
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

    val gitRepoName = MutableViewModelStateFlow("")
    val gitRepoUrl = MutableViewModelStateFlow("")
    val gitRepoSshKeyName = MutableViewModelStateFlow("")

    private val _addGitRepoRes = MutableViewModelStateFlow(Result.success(Unit))
    val addGitRepoRes = _addGitRepoRes.flow

    fun addGitRepo() {
        viewModelScope.launch {
            val name = gitRepoName.value.trim()
            val url = gitRepoUrl.value.trim()
            val sshKeyName = gitRepoSshKeyName.value.trim()
            if (name.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Name may not be empty")) }
                return@launch
            }
            if (url.isEmpty()) {
                _addGitRepoRes.update { Result.failure(Exception("Url may not be empty")) }
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
                    sshKeyName = sshKeyName,
                )
            )
            _addGitRepoRes.update { res }
            res.onSuccess {
                gitRepoName.update { "" }
                gitRepoUrl.update { "" }
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
