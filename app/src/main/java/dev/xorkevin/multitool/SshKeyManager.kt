package dev.xorkevin.multitool

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun SshKeyManager() = ViewModelScope(SshKeyManagerViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            SshKeyManagerInput()
        }
        SshKeyManagerList()
    }
}

@Composable
fun SshKeyManagerInput() {
    val sshKeyManagerViewModel: SshKeyManagerViewModel = scopedViewModel()

    var name by sshKeyManagerViewModel.sshKeyName.collectAsStateWithLifecycle()
    var keyStr by sshKeyManagerViewModel.sshKeyStr.collectAsStateWithLifecycle()
    var passphrase by sshKeyManagerViewModel.sshKeyPassphrase.collectAsStateWithLifecycle()
    val storeRes by sshKeyManagerViewModel.storeSshKeyRes.collectAsStateWithLifecycle()

    Text(
        text = "Add a key",
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
        value = keyStr,
        onValueChange = { keyStr = it },
        label = { Text(text = "Key") },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { keyStr = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan key"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(text = "Passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            QRScannerLauncher(
                onScan = { passphrase = (it ?: "").trim() },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan passphrase"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    Button(
        onClick = { sshKeyManagerViewModel.storeSshKey() },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Store key")
    }
    storeRes.onFailure {
        Text(
            text = "Failed to store key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun SshKeyManagerList() {
    val sshKeyManagerViewModel: SshKeyManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        sshKeyManagerViewModel.refreshSshKeys()
    }

    val sshKeys by sshKeyManagerViewModel.sshKeys.collectAsStateWithLifecycle()
    val displayDeleteSshKeyModal by sshKeyManagerViewModel.displayDeleteSshKeyModal.collectAsStateWithLifecycle()

    TextButton(
        onClick = { sshKeyManagerViewModel.refreshSshKeys() },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Refresh")
    }
    sshKeys.onFailure {
        Text(
            text = "Failed to get keys: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    sshKeys.onSuccess { keys ->
        Text(
            text = "${keys.size} Keys",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        keys.forEach {
            ListItem(headlineContent = {
                Text(text = it.name)
            }, trailingContent = {
                TextButton(
                    onClick = { sshKeyManagerViewModel.promptDeleteSshKey(it.name) },
                    modifier = Modifier.padding(8.dp, 0.dp),
                ) {
                    Text(text = "Delete")
                }
            })
        }
    }
    if (displayDeleteSshKeyModal) {
        val candidateSshKeyDeleteName by sshKeyManagerViewModel.candidateSshKeyDeleteName.collectAsStateWithLifecycle()
        val deleteRes by sshKeyManagerViewModel.deleteSshKeyRes.collectAsStateWithLifecycle()

        AlertDialog(title = { Text(text = "Delete ssh key") }, text = {
            Column {
                Text(text = "This will delete the ssh key \"$candidateSshKeyDeleteName\", making it unavailable for use to pull git repositories.")
                deleteRes.onFailure {
                    Text(
                        text = "Failed to delete key: ${it.toString()}",
                        modifier = Modifier
                            .padding(16.dp, 8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }, onDismissRequest = { sshKeyManagerViewModel.dismissDeleteSshKey() }, confirmButton = {
            TextButton(
                onClick = {
                    sshKeyManagerViewModel.deleteSshKey(candidateSshKeyDeleteName)
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { sshKeyManagerViewModel.dismissDeleteSshKey() }) {
                Text("Cancel")
            }
        })
    }
}

class SshKeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    private val _sshKeys =
        MutableViewModelStateFlow(Result.success(listOf<KeyStoreService.SshKeyNameTuple>()))
    val sshKeys = _sshKeys.flow

    fun refreshSshKeys() {
        viewModelScope.launch {
            val res = keyStore.getAllSshKeys()
            _sshKeys.update { res }
        }
    }

    private val _candidateSshKeyDeleteName = MutableViewModelStateFlow("")
    val candidateSshKeyDeleteName = _candidateSshKeyDeleteName.flow
    private val _displayDeleteSshKeyModal = MutableViewModelStateFlow(false)
    val displayDeleteSshKeyModal = _displayDeleteSshKeyModal.flow
    fun promptDeleteSshKey(name: String) {
        _candidateSshKeyDeleteName.update { name }
        _displayDeleteSshKeyModal.update { true }
    }

    fun dismissDeleteSshKey() {
        _displayDeleteSshKeyModal.update { false }
        _deleteSshKeyRes.update { Result.success(Unit) }
        _candidateSshKeyDeleteName.update { "" }
    }

    private val _deleteSshKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val deleteSshKeyRes = _deleteSshKeyRes.flow

    fun deleteSshKey(name: String) {
        viewModelScope.launch {
            val res = keyStore.deleteSshKey(name)
            _deleteSshKeyRes.update { res }
            res.onSuccess {
                dismissDeleteSshKey()
                refreshSshKeys()
            }
        }
    }

    val sshKeyName = MutableViewModelStateFlow("")
    val sshKeyStr = MutableViewModelStateFlow("")
    val sshKeyPassphrase = MutableViewModelStateFlow("")

    private val _storeSshKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val storeSshKeyRes = _storeSshKeyRes.flow

    fun storeSshKey() {
        viewModelScope.launch {
            val name = sshKeyName.value.trim()
            val keyStr = sshKeyStr.value
            val passphrase = sshKeyPassphrase.value
            if (name.isEmpty()) {
                _storeSshKeyRes.update { Result.failure(Exception("Name may not be empty")) }
                return@launch
            }
            if (keyStr.isEmpty()) {
                _storeSshKeyRes.update { Result.failure(Exception("Key may not be empty")) }
                return@launch
            }

            val res = keyStore.storeSshKey(
                name = name,
                keyStr = keyStr,
                passphrase = passphrase,
            )
            _storeSshKeyRes.update { res }
            res.onSuccess {
                sshKeyName.update { "" }
                sshKeyStr.update { "" }
                sshKeyPassphrase.update { "" }
                refreshSshKeys()
            }
        }
    }

    companion object : ScopedViewModelFactory<SshKeyManagerViewModel> {
        override fun create(app: Application): SshKeyManagerViewModel {
            app as MainApplication
            return SshKeyManagerViewModel(app.container.keyStore)
        }
    }
}
