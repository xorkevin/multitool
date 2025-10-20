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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        RootKeyManagerInput()
    }
}

@Composable
fun RootKeyManagerInput() {
    val keyManagerViewModel: KeyManagerViewModel = scopedViewModel()

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val unlocked by keyManagerViewModel.unlockState.collectAsStateWithLifecycle()
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
            Button(
                onClick = { coroutineScope.launch { keyManagerViewModel.biometricUnlock(context) } },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Text(text = "Biometric unlock")
            }
            Button(
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

    val biometricResult by keyManagerViewModel.biometricResult.collectAsStateWithLifecycle()
    val hasBiometric by keyManagerViewModel.biometricState.collectAsStateWithLifecycle()
    if (hasBiometric) {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.removeBiometric() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Remove biometric")
        }
    } else {
        Button(
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

    var rootKeyPassword by keyManagerViewModel.rootKeyPassword.collectAsStateWithLifecycle()
    val rootKeyResult by keyManagerViewModel.rootKeyResult.collectAsStateWithLifecycle()

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
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Scan root key password")
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth()) {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.generateRootKey() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Generate root key")
        }
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.deleteRootKey() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Delete root key")
        }
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

class KeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    val unlockState =
        keyStore.unlockState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val biometricState = keyStore.biometricState

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

    suspend fun generateRootKey() {
        val password = rootKeyPassword.value.toByteArray()
        val res = keyStore.generateRootKey(password)
        _rootKeyResult.update { res }
        res.onSuccess {
            rootKeyPassword.update { "" }
        }
    }

    suspend fun deleteRootKey() {
        val res = keyStore.deleteRootKey()
        _rootKeyResult.update { res }
    }

    private val _biometricResult = MutableViewModelStateFlow(Result.success(Unit))
    val biometricResult = _biometricResult.flow

    suspend fun setupBiometric(activityCtx: Context) {
        val res = keyStore.setupBiometric(activityCtx)
        _biometricResult.update { res }
    }

    suspend fun removeBiometric() {
        val res = keyStore.removeBiometric()
        _biometricResult.update { res }
    }

    companion object : ScopedViewModelFactory<KeyManagerViewModel> {
        override fun create(app: Application): KeyManagerViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            return KeyManagerViewModel(keyStore)
        }
    }
}
