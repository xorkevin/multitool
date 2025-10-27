package dev.xorkevin.multitool

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
fun VaultUnlocker(content: @Composable (lock: () -> Unit) -> Unit) = ViewModelScope(
    VaultUnlockerViewModel::class
) {
    val vaultUnlockerViewModel: VaultUnlockerViewModel = scopedViewModel()

    LaunchedEffect(Unit) {
        vaultUnlockerViewModel.refreshState()
    }

    val context = LocalContext.current

    val isSetup by vaultUnlockerViewModel.setupState.collectAsStateWithLifecycle()
    val unlocked by vaultUnlockerViewModel.unlockState.collectAsStateWithLifecycle()

    val hasBiometric by vaultUnlockerViewModel.biometricState.collectAsStateWithLifecycle()
    var password by vaultUnlockerViewModel.password.collectAsStateWithLifecycle()
    val unlockResult by vaultUnlockerViewModel.unlockResult.collectAsStateWithLifecycle()

    if (!isSetup) {
        Text(text = "Vault not setup")
    } else if (unlocked) {
        content({ vaultUnlockerViewModel.lock() })
    } else {
        Text(
            text = "Unlock the vault",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
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
        Button(
            onClick = { vaultUnlockerViewModel.passwordUnlock() },
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth(),
        ) {
            Text(text = "Unlock")
        }
        if (hasBiometric) {
            TextButton(
                onClick = { vaultUnlockerViewModel.biometricUnlock(context) },
                modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth(),
            ) {
                Text(text = "Biometric unlock")
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


class VaultUnlockerViewModel(private val keyStore: KeyStoreService) : ViewModel() {
    val unlockState =
        keyStore.unlockState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val setupState = keyStore.setupState
    val biometricState = keyStore.biometricState

    fun refreshState() {
        viewModelScope.launch {
            keyStore.refreshState()
        }
    }

    fun lock() {
        viewModelScope.launch {
            keyStore.lock()
        }
    }

    private val _unlockResult = MutableViewModelStateFlow(Result.success(Unit))
    val unlockResult = _unlockResult.flow

    fun biometricUnlock(activityCtx: Context) {
        viewModelScope.launch {
            val res = keyStore.biometricUnlock(activityCtx)
            _unlockResult.update { res }
        }
    }

    val password = MutableViewModelStateFlow("")

    fun passwordUnlock() {
        viewModelScope.launch {
            val keyBytes = password.value.toByteArray()
            val res = keyStore.passwordUnlock(keyBytes)
            _unlockResult.update { res }
            res.onSuccess {
                password.update { "" }
            }
        }
    }

    companion object : ScopedViewModelFactory<VaultUnlockerViewModel> {
        override fun create(app: Application): VaultUnlockerViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            return VaultUnlockerViewModel(keyStore)
        }
    }
}
