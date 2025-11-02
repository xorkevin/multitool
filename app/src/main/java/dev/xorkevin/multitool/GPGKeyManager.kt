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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@Composable
fun GPGKeyManager() = ViewModelScope(GPGKeyManagerViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        VaultUnlocker {
            GPGKeyManagerInput()
            GPGKeyManagerList()
        }
    }
}

@Composable
fun GPGKeyManagerInput() {
    val gpgKeyManagerViewModel: GPGKeyManagerViewModel = scopedViewModel()

    var name by gpgKeyManagerViewModel.gpgKeyName.collectAsStateWithLifecycle()
    var keyStr by gpgKeyManagerViewModel.gpgKeyStr.collectAsStateWithLifecycle()
    var passphrase by gpgKeyManagerViewModel.gpgKeyPassphrase.collectAsStateWithLifecycle()
    val storeRes by gpgKeyManagerViewModel.storeGPGKeyRes.collectAsStateWithLifecycle()

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
        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        trailingIcon = {
            QRScannerLauncher(
                onScan = { keyStr = it ?: "" },
                modifier = Modifier.padding(8.dp, 8.dp),
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
        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            QRScannerLauncher(
                onScan = { passphrase = (it ?: "").trim() },
                modifier = Modifier.padding(8.dp, 8.dp),
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
        onClick = { gpgKeyManagerViewModel.storeGPGKey() },
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
fun GPGKeyManagerList() {
    val gpgKeyManagerViewModel: GPGKeyManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        gpgKeyManagerViewModel.refreshGPGKeys()
    }

    val gpgKeys by gpgKeyManagerViewModel.gpgKeys.collectAsStateWithLifecycle()
    val displayDeleteSshKeyModal by gpgKeyManagerViewModel.displayDeleteGPGKeyModal.collectAsStateWithLifecycle()

    TextButton(
        onClick = { gpgKeyManagerViewModel.refreshGPGKeys() },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Refresh")
    }
    gpgKeys.onFailure {
        Text(
            text = "Failed to get keys: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    gpgKeys.onSuccess { keys ->
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
                    onClick = { gpgKeyManagerViewModel.promptDeleteGPGKey(it.name) },
                    modifier = Modifier.padding(8.dp, 0.dp),
                ) {
                    Text(text = "Delete")
                }
            })
        }
    }
    if (displayDeleteSshKeyModal) {
        val candidateGPGKeyDeleteName by gpgKeyManagerViewModel.candidateGPGKeyDeleteName.collectAsStateWithLifecycle()
        val deleteRes by gpgKeyManagerViewModel.deleteGPGKeyRes.collectAsStateWithLifecycle()

        AlertDialog(title = { Text(text = "Delete gpg key") }, text = {
            Column {
                Text(text = "This will delete the gpg key \"$candidateGPGKeyDeleteName\", making it unavailable for use to decrypt stored passwords.")
                deleteRes.onFailure {
                    Text(
                        text = "Failed to delete key: ${it.toString()}",
                        modifier = Modifier
                            .padding(16.dp, 8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }, onDismissRequest = { gpgKeyManagerViewModel.dismissDeleteGPGKey() }, confirmButton = {
            TextButton(
                onClick = {
                    gpgKeyManagerViewModel.deleteGPGKey(candidateGPGKeyDeleteName)
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { gpgKeyManagerViewModel.dismissDeleteGPGKey() }) {
                Text("Cancel")
            }
        })
    }
}

class GPGKeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    private val _gpgKeys =
        MutableViewModelStateFlow(Result.success(listOf<KeyStoreService.GPGKeyNameTuple>()))
    val gpgKeys = _gpgKeys.flow

    fun refreshGPGKeys() {
        viewModelScope.launch {
            val res = keyStore.getAllGPGKeys()
            _gpgKeys.update { res }
        }
    }

    private val _candidateGPGKeyDeleteName = MutableViewModelStateFlow("")
    val candidateGPGKeyDeleteName = _candidateGPGKeyDeleteName.flow
    private val _displayDeleteGPGKeyModal = MutableViewModelStateFlow(false)
    val displayDeleteGPGKeyModal = _displayDeleteGPGKeyModal.flow
    fun promptDeleteGPGKey(name: String) {
        _candidateGPGKeyDeleteName.update { name }
        _displayDeleteGPGKeyModal.update { true }
    }

    fun dismissDeleteGPGKey() {
        _displayDeleteGPGKeyModal.update { false }
        _deleteGPGKeyRes.update { Result.success(Unit) }
        _candidateGPGKeyDeleteName.update { "" }
    }

    private val _deleteGPGKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val deleteGPGKeyRes = _deleteGPGKeyRes.flow

    fun deleteGPGKey(name: String) {
        viewModelScope.launch {
            val res = keyStore.deleteGPGKey(name)
            _deleteGPGKeyRes.update { res }
            res.onSuccess {
                dismissDeleteGPGKey()
                refreshGPGKeys()
            }
        }
    }

    val gpgKeyName = MutableViewModelStateFlow("")
    val gpgKeyStr = MutableViewModelStateFlow("")
    val gpgKeyPassphrase = MutableViewModelStateFlow("")

    private val _storeGPGKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val storeGPGKeyRes = _storeGPGKeyRes.flow

    fun storeGPGKey() {
        viewModelScope.launch {
            val name = gpgKeyName.value.trim()
            val keyStr = gpgKeyStr.value
            val passphrase = gpgKeyPassphrase.value
            if (name.isEmpty()) {
                _storeGPGKeyRes.update { Result.failure(Exception("Name may not be empty")) }
                return@launch
            }
            if (keyStr.isEmpty()) {
                _storeGPGKeyRes.update { Result.failure(Exception("Key may not be empty")) }
                return@launch
            }

            val res = keyStore.storeGPGKey(
                name = name,
                keyStr = keyStr,
                passphrase = passphrase,
            )
            _storeGPGKeyRes.update { res }
            res.onSuccess {
                gpgKeyName.update { "" }
                gpgKeyStr.update { "" }
                gpgKeyPassphrase.update { "" }
                refreshGPGKeys()
            }
        }
    }

    companion object : ScopedViewModelFactory<GPGKeyManagerViewModel> {
        override fun create(app: Application): GPGKeyManagerViewModel {
            app as MainApplication
            return GPGKeyManagerViewModel(app.container.keyStore)
        }
    }
}
