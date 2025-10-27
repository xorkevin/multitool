package dev.xorkevin.multitool

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun RootKeyManager() = ViewModelScope(RootKeyManagerViewModel::class) {
    val rootKeyManagerViewModel: RootKeyManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        rootKeyManagerViewModel.refreshState()
    }

    val isSetup by rootKeyManagerViewModel.setupState.collectAsStateWithLifecycle()

    if (isSetup) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.verticalScroll(scrollState)) {
            VaultUnlocker { lock ->
                Text(
                    text = "Lock the vault",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth()
                )
                Button(
                    onClick = { lock() },
                    modifier = Modifier
                        .padding(16.dp, 8.dp)
                        .fillMaxWidth(),
                ) {
                    Text(text = "Lock")
                }
            }
            BiometricManager()
            RootKeyManagerDeleter()
        }
    } else {
        RootKeyManagerSetup()
    }
}

@Composable
fun BiometricManager() {
    val rootKeyManagerViewModel: RootKeyManagerViewModel = scopedViewModel()

    val context = LocalContext.current

    val unlocked by rootKeyManagerViewModel.unlockState.collectAsStateWithLifecycle()
    val displayRemoveBiometricModal by rootKeyManagerViewModel.displayRemoveBiometricModal.collectAsStateWithLifecycle()
    val biometricResult by rootKeyManagerViewModel.biometricResult.collectAsStateWithLifecycle()
    val hasBiometric by rootKeyManagerViewModel.biometricState.collectAsStateWithLifecycle()

    Text(
        text = "Manage biometric auth",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    )
    if (hasBiometric) {
        TextButton(
            onClick = { rootKeyManagerViewModel.showRemoveBiometric() },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Remove biometric")
        }
        biometricResult.onFailure {
            Text(
                text = it.toString(),
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
            )
        }
    } else if (unlocked) {
        TextButton(
            onClick = { rootKeyManagerViewModel.setupBiometric(context) },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Setup biometric")
        }
    }
    if (displayRemoveBiometricModal) {
        AlertDialog(
            title = { Text(text = "Remove biometric auth") },
            text = {
                Column {
                    Text(text = "This will remove the ability to unlock the vault with biometrics and require a password only.")
                    biometricResult.onFailure {
                        Text(
                            text = it.toString(),
                            modifier = Modifier
                                .padding(16.dp, 8.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            },
            onDismissRequest = { rootKeyManagerViewModel.dismissRemoveBiometric() },
            confirmButton = {
                TextButton(
                    onClick = {
                        rootKeyManagerViewModel.removeBiometric()
                    }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { rootKeyManagerViewModel.dismissRemoveBiometric() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun RootKeyManagerDeleter() {
    val rootKeyManagerViewModel: RootKeyManagerViewModel = scopedViewModel()

    val displayDeleteRootKeyModal by rootKeyManagerViewModel.displayDeleteRootKeyModal.collectAsStateWithLifecycle()
    val rootKeyResult by rootKeyManagerViewModel.rootKeyResult.collectAsStateWithLifecycle()

    Text(
        text = "Danger zone",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    )
    TextButton(
        onClick = { rootKeyManagerViewModel.showDeleteRootKeyModal() },
        modifier = Modifier.padding(16.dp, 8.dp),
    ) {
        Text(text = "Delete root key")
    }
    if (displayDeleteRootKeyModal) {
        var deleteRootKeyConfirmationText by rootKeyManagerViewModel.deleteRootKeyModalConfirmationText.collectAsStateWithLifecycle()
        AlertDialog(icon = {
            Icon(
                imageVector = Icons.Filled.Warning, contentDescription = "Danger"
            )
        }, title = { Text(text = "Delete root key") }, text = {
            Column {
                Text(text = "This will delete the root key making all uploaded keys irrecoverable, requiring them and their passphrases to be reuploaded. Type \"DELETE ROOT KEY\" to proceed.")
                TextField(
                    value = deleteRootKeyConfirmationText,
                    onValueChange = { deleteRootKeyConfirmationText = it },
                    label = { Text(text = "Confirmation Text") },
                    modifier = Modifier.padding(0.dp, 8.dp),
                )
                rootKeyResult.onFailure {
                    Text(
                        text = it.toString(),
                        modifier = Modifier
                            .padding(16.dp, 8.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }, onDismissRequest = { rootKeyManagerViewModel.dismissDeleteRootKey() }, confirmButton = {
            TextButton(
                onClick = {
                    rootKeyManagerViewModel.deleteRootKey()
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { rootKeyManagerViewModel.dismissDeleteRootKey() }) {
                Text("Cancel")
            }
        })
    }
}

@Composable
fun RootKeyManagerSetup() {
    val rootKeyManagerViewModel: RootKeyManagerViewModel = scopedViewModel()
    val scrollState = rememberScrollState()

    var rootKeyPassword by rootKeyManagerViewModel.rootKeyPassword.collectAsStateWithLifecycle()
    val rootKeyResult by rootKeyManagerViewModel.rootKeyResult.collectAsStateWithLifecycle()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        TextField(
            value = rootKeyPassword,
            onValueChange = { rootKeyPassword = it },
            label = { Text(text = "Root Key Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                QRScannerLauncher(
                    onScan = { rootKeyPassword = (it ?: "").trim() },
                    modifier = Modifier.padding(16.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Scan root key password"
                    )
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        Button(
            onClick = { rootKeyManagerViewModel.generateRootKey() },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Generate root key")
        }
        rootKeyResult.onFailure {
            Text(
                text = it.toString(),
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

class RootKeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    val unlockState =
        keyStore.unlockState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val setupState = keyStore.setupState
    val biometricState = keyStore.biometricState

    fun refreshState() {
        viewModelScope.launch {
            keyStore.refreshState()
        }
    }

    val rootKeyPassword = MutableViewModelStateFlow("")

    private val _rootKeyResult = MutableViewModelStateFlow(Result.success(Unit))
    val rootKeyResult = _rootKeyResult.flow
    private val _displayDeleteRootKeyModal = MutableViewModelStateFlow(false)
    val displayDeleteRootKeyModal = _displayDeleteRootKeyModal.flow
    val deleteRootKeyModalConfirmationText = MutableViewModelStateFlow("")

    fun generateRootKey() {
        viewModelScope.launch {
            val password = rootKeyPassword.value.toByteArray()
            val res = keyStore.generateRootKey(password)
            _rootKeyResult.update { res }
            res.onSuccess {
                rootKeyPassword.update { "" }
            }
        }
    }

    fun deleteRootKey() {
        if (deleteRootKeyModalConfirmationText.value != "DELETE ROOT KEY") {
            return
        }
        viewModelScope.launch {
            val res = keyStore.deleteRootKey()
            _rootKeyResult.update { res }
            res.onSuccess {
                dismissDeleteRootKey()
            }
        }
    }

    fun showDeleteRootKeyModal() {
        _displayDeleteRootKeyModal.update { true }
    }

    fun dismissDeleteRootKey() {
        _displayDeleteRootKeyModal.update { false }
        _rootKeyResult.update { Result.success(Unit) }
        deleteRootKeyModalConfirmationText.update { "" }
    }

    private val _biometricResult = MutableViewModelStateFlow(Result.success(Unit))
    val biometricResult = _biometricResult.flow
    private val _displayRemoveBiometricModal = MutableViewModelStateFlow(false)
    val displayRemoveBiometricModal = _displayRemoveBiometricModal.flow

    fun setupBiometric(activityCtx: Context) {
        viewModelScope.launch {
            val res = keyStore.setupBiometric(activityCtx)
            _biometricResult.update { res }
        }
    }

    fun removeBiometric() {
        viewModelScope.launch {
            val res = keyStore.removeBiometric()
            _biometricResult.update { res }
            res.onSuccess {
                dismissRemoveBiometric()
            }
        }
    }

    fun showRemoveBiometric() {
        _displayRemoveBiometricModal.update { true }
    }

    fun dismissRemoveBiometric() {
        _displayRemoveBiometricModal.update { false }
        _biometricResult.update { Result.success(Unit) }
    }

    companion object : ScopedViewModelFactory<RootKeyManagerViewModel> {
        override fun create(app: Application): RootKeyManagerViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            return RootKeyManagerViewModel(keyStore)
        }
    }
}
