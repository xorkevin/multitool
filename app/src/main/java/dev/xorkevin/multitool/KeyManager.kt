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
    val unlockResult by keyManagerViewModel.unlockResult.collectAsStateWithLifecycle()

    if (unlocked) {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.lock() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Lock")
        }
    } else {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.unlock(context) } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Unlock")
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

    var rootKeyStr by keyManagerViewModel.rootKeyStr.collectAsStateWithLifecycle()
    val encryptRootKeyResult by keyManagerViewModel.encryptRootKeyResult.collectAsStateWithLifecycle()

    TextField(
        value = rootKeyStr,
        onValueChange = { rootKeyStr = it },
        label = { Text(text = "Root Key") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            QRScannerLauncher(
                onScan = { rootKeyStr = (it ?: "").trim() },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Scan root key")
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth()) {
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.unlockWithKey() } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Unlock with key")
        }
        Button(
            onClick = { coroutineScope.launch { keyManagerViewModel.encryptRootKey(context) } },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Encrypt and store root key")
        }
    }
    encryptRootKeyResult.onFailure {
        Text(
            text = "Failed to encrypt and store root key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth(),
        )
    }

    val deleteRootKeyResult by keyManagerViewModel.deleteRootKeyResult.collectAsStateWithLifecycle()

    Button(
        onClick = { coroutineScope.launch { keyManagerViewModel.deleteRootKey() } },
        modifier = Modifier.padding(16.dp, 8.dp),
    ) {
        Text(text = "Delete root key")
    }
    deleteRootKeyResult.onFailure {
        Text(
            text = "Failed to delete root key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth(),
        )
    }
}

class KeyManagerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    val unlockState =
        keyStore.unlockState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    suspend fun lock() {
        keyStore.lock()
    }

    private val _unlockResult = MutableViewModelStateFlow(Result.success(Unit))
    val unlockResult = _unlockResult.flow

    suspend fun unlock(activityCtx: Context) {
        val res = keyStore.unlock(activityCtx)
        _unlockResult.update { res }
    }

    val rootKeyStr = MutableViewModelStateFlow("")

    suspend fun unlockWithKey() {
        val keyBytes = rootKeyStr.value.toByteArray()
        keyStore.setRootKey(keyBytes)
        rootKeyStr.update { "" }
    }

    private val _encryptRootKeyResult = MutableViewModelStateFlow(Result.success(Unit))
    val encryptRootKeyResult = _encryptRootKeyResult.flow

    suspend fun encryptRootKey(activityCtx: Context) {
        val keyBytes = rootKeyStr.value.toByteArray()
        val res = keyStore.encryptRootKey(keyBytes, activityCtx)
        _encryptRootKeyResult.update { res }
        res.onSuccess {
            rootKeyStr.update { "" }
        }
    }

    private val _deletetRootKeyResult = MutableViewModelStateFlow(Result.success(Unit))
    val deleteRootKeyResult = _deletetRootKeyResult.flow

    suspend fun deleteRootKey() {
        val res = keyStore.deleteRootKey()
        _deletetRootKeyResult.update { res }
    }

    companion object : ScopedViewModelFactory<KeyManagerViewModel> {
        override fun create(app: Application): KeyManagerViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            return KeyManagerViewModel(keyStore)
        }
    }
}
