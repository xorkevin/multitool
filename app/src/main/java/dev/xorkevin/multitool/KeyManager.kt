package dev.xorkevin.multitool

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
fun KeyManager() = ViewModelScope(KeyManagerViewModel::class) {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        keyManagerViewModel.refreshState()
    }

    val isSetup by keyManagerViewModel.setupState.collectAsStateWithLifecycle()

    if (isSetup) {
        RootKeyManagerInput()
    } else {
        RootKeyManagerSetup()
    }
}


@Composable
fun RootKeyManagerInput() {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        RootKeyUnlocker()
        BiometricManager()
        RootKeyManagerDeleter()
    }
}

@Composable
fun RootKeyUnlocker() {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val unlocked by keyManagerViewModel.unlockState.collectAsStateWithLifecycle()

    val hasBiometric by keyManagerViewModel.biometricState.collectAsStateWithLifecycle()
    var password by keyManagerViewModel.password.collectAsStateWithLifecycle()
    val unlockResult by keyManagerViewModel.unlockResult.collectAsStateWithLifecycle()

    if (unlocked) {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.lock() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Lock")
        }
    } else {
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                QRScannerLauncher(
                    onScan = { password = (it ?: "").trim() },
                    modifier = Modifier.padding(16.dp, 8.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Scan password")
                }
            },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            if (hasBiometric) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            keyManagerViewModel.biometricUnlock(
                                context
                            )
                        }
                    },
                    modifier = Modifier.padding(16.dp, 8.dp),
                ) {
                    Text(text = "Biometric unlock")
                }
            }
            TextButton(
                onClick = { coroutineScope.launch { keyManagerViewModel.passwordUnlock() } },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Text(text = "Password unlock")
            }
        }
        unlockResult.onFailure {
            Text(
                text = "Failed to unlock vault: ${it.toString()}",
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BiometricManager() {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val unlocked by keyManagerViewModel.unlockState.collectAsStateWithLifecycle()
    val displayRemoveBiometricModal by keyManagerViewModel.displayRemoveBiometricModal.collectAsStateWithLifecycle()
    val biometricResult by keyManagerViewModel.biometricResult.collectAsStateWithLifecycle()
    val hasBiometric by keyManagerViewModel.biometricState.collectAsStateWithLifecycle()
    if (hasBiometric) {
        TextButton(
            onClick = { coroutineScope.launch { keyManagerViewModel.showRemoveBiometric() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Remove biometric")
        }
    } else if (unlocked) {
        TextButton(
            onClick = { coroutineScope.launch { keyManagerViewModel.setupBiometric(context) } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Setup biometric")
        }
    }
    biometricResult.onFailure {
        Text(
            text = it.toString(),
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth(),
        )
    }
    if (displayRemoveBiometricModal) {
        AlertDialog(title = { Text(text = "Remove biometric auth") }, text = {
            Column {
                Text(text = "This will remove the ability to unlock the vault with biometrics and require a password only.")
            }
        }, onDismissRequest = { keyManagerViewModel.dismissRemoveBiometric() }, confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch { keyManagerViewModel.removeBiometric() }
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { keyManagerViewModel.dismissRemoveBiometric() }) {
                Text("Cancel")
            }
        })
    }
}

@Composable
fun RootKeyManagerDeleter() {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()

    val coroutineScope = rememberCoroutineScope()

    val displayDeleteRootKeyModal by keyManagerViewModel.displayDeleteRootKeyModal.collectAsStateWithLifecycle()
    val rootKeyResult by keyManagerViewModel.rootKeyResult.collectAsStateWithLifecycle()

    TextButton(
        onClick = { keyManagerViewModel.showDeleteRootKeyModal() },
        modifier = Modifier.padding(16.dp, 8.dp),
    ) {
        Text(text = "Delete root key")
    }
    rootKeyResult.onFailure {
        Text(
            text = it.toString(),
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth(),
        )
    }
    if (displayDeleteRootKeyModal) {
        var deleteRootKeyConfirmationText by keyManagerViewModel.deleteRootKeyModalConfirmationText.collectAsStateWithLifecycle()
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
            }
        }, onDismissRequest = { keyManagerViewModel.dismissDeleteRootKey() }, confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch { keyManagerViewModel.deleteRootKey() }
                }) {
                Text("Confirm")
            }
        }, dismissButton = {
            TextButton(onClick = { keyManagerViewModel.dismissDeleteRootKey() }) {
                Text("Cancel")
            }
        })
    }
}

@Composable
fun RootKeyManagerSetup() {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var rootKeyPassword by keyManagerViewModel.rootKeyPassword.collectAsStateWithLifecycle()
    val rootKeyResult by keyManagerViewModel.rootKeyResult.collectAsStateWithLifecycle()

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
            onClick = { coroutineScope.launch { keyManagerViewModel.generateRootKey() } },
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

class KeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    val unlockState =
        keyStore.unlockState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val setupState = keyStore.setupState
    val biometricState = keyStore.biometricState

    suspend fun refreshState() {
        keyStore.refreshState()
    }

    suspend fun lock() {
        keyStore.lock()
    }

    private val _unlockResult = MutableViewModelStateFlow(Result.success(Unit))
    val unlockResult = _unlockResult.flow

    suspend fun biometricUnlock(activityCtx: Context) {
        val res = keyStore.biometricUnlock(activityCtx)
        _unlockResult.update { res }
    }

    val password = MutableViewModelStateFlow("")

    suspend fun passwordUnlock() {
        val keyBytes = password.value.toByteArray()
        val res = keyStore.passwordUnlock(keyBytes)
        _unlockResult.update { res }
        res.onSuccess {
            password.update { "" }
        }
    }

    val rootKeyPassword = MutableViewModelStateFlow("")

    private val _rootKeyResult = MutableViewModelStateFlow(Result.success(Unit))
    val rootKeyResult = _rootKeyResult.flow
    private val _displayDeleteRootKeyModal = MutableViewModelStateFlow(false)
    val displayDeleteRootKeyModal = _displayDeleteRootKeyModal.flow
    val deleteRootKeyModalConfirmationText = MutableViewModelStateFlow("")

    suspend fun generateRootKey() {
        val password = rootKeyPassword.value.toByteArray()
        val res = keyStore.generateRootKey(password)
        _rootKeyResult.update { res }
        res.onSuccess {
            rootKeyPassword.update { "" }
        }
    }

    suspend fun deleteRootKey() {
        if (deleteRootKeyModalConfirmationText.value != "DELETE ROOT KEY") {
            return
        }
        val res = keyStore.deleteRootKey()
        _rootKeyResult.update { res }
        res.onSuccess {
            dismissDeleteRootKey()
        }
    }

    fun showDeleteRootKeyModal() {
        _displayDeleteRootKeyModal.update { true }
    }

    fun dismissDeleteRootKey() {
        _displayDeleteRootKeyModal.update { false }
        deleteRootKeyModalConfirmationText.update { "" }
    }

    private val _biometricResult = MutableViewModelStateFlow(Result.success(Unit))
    val biometricResult = _biometricResult.flow
    private val _displayRemoveBiometricModal = MutableViewModelStateFlow(false)
    val displayRemoveBiometricModal = _displayRemoveBiometricModal.flow

    suspend fun setupBiometric(activityCtx: Context) {
        val res = keyStore.setupBiometric(activityCtx)
        _biometricResult.update { res }
    }

    suspend fun removeBiometric() {
        val res = keyStore.removeBiometric()
        _biometricResult.update { res }
        res.onSuccess {
            dismissRemoveBiometric()
        }
    }

    fun showRemoveBiometric() {
        _displayRemoveBiometricModal.update { true }
    }

    fun dismissRemoveBiometric() {
        _displayRemoveBiometricModal.update { false }
    }

    companion object : ScopedViewModelFactory<KeyManagerViewModel> {
        override fun create(app: Application): KeyManagerViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            return KeyManagerViewModel(keyStore)
        }
    }
}
