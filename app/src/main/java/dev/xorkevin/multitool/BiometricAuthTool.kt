package dev.xorkevin.multitool

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel

@Composable
fun BiometricAuthTool() = ViewModelScope(BiometricAuthToolViewModel::class) {
    val biometricAuthToolViewModel: BiometricAuthToolViewModel = scopedViewModel()
    val scrollState = rememberScrollState()
    var success by biometricAuthToolViewModel.success.collectAsStateWithLifecycle()
    var error by biometricAuthToolViewModel.error.collectAsStateWithLifecycle()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        BiometricAuthLauncher(
            title = "Test Biometric Authentication",
            onSuccess = { success = true; error = "" },
            onError = { success = false; error = it },
            modifier = Modifier.padding(16.dp, 8.dp),
        ) {
            Text(text = "Test Biometric Auth", modifier = Modifier.padding(16.dp, 8.dp))
        }
        Text(
            text = if (success) {
                "success"
            } else {
                ""
            },
            modifier = Modifier.padding(16.dp, 8.dp),
        )
        Text(
            text = if (error != "") {
                "Error: $error"
            } else {
                ""
            },
            modifier = Modifier.padding(16.dp, 8.dp),
        )
    }
}

class BiometricAuthToolViewModel : ViewModel() {
    val success = MutableViewModelStateFlow(false)
    val error = MutableViewModelStateFlow("")
}

@Composable
fun BiometricAuthLauncher(
    title: String,
    modifier: Modifier = Modifier,
    onSuccess: () -> Unit = {},
    onError: (err: String) -> Unit = {},
    content: @Composable (RowScope.() -> Unit),
) = ViewModelScope(BiometricAuthViewModel::class) {
    val context = LocalContext.current
    val activity: FragmentActivity? = context.getActivity()
    val biometricManager = BiometricManager.from(context)
    val canBiometricAuth =
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    val biometricAuthViewModel: BiometricAuthViewModel = scopedViewModel()
    var showError by biometricAuthViewModel.showError.collectAsStateWithLifecycle()

    Button(
        onClick = {
            if (!canBiometricAuth) {
                showError = "Biometric authentication is not available on this device"
            } else if (activity == null) {
                showError = "Failed to set up biometric prompt due to missing activity"
            } else {
                authWithBiometric(
                    title = title, activity = activity, onSuccess = onSuccess, onError = onError
                )
            }
        },
        modifier = modifier,
        content = content,
    )
    if (showError != "") {
        Dialog(
            onDismissRequest = { showError = "" },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = true,
            ),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .background(Color.Black),
            ) {
                Text(
                    text = showError,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
                Button(
                    onClick = { showError = "" }, modifier = Modifier.padding(16.dp, 8.dp)
                ) {
                    Text(text = "Close")
                }
            }
        }
    }
}

fun authWithBiometric(
    title: String,
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (err: String) -> Unit,
) {
    val biometricPrompt = BiometricPrompt(
        activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder().run {
        setTitle(title)
        setNegativeButtonText("Cancel")
        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        setConfirmationRequired(true)
        build()
    }
    biometricPrompt.authenticate(promptInfo)
}

fun authWithBiometricCrypto(
    title: String,
    activity: FragmentActivity,
    onSuccess: (o: BiometricPrompt.CryptoObject) -> Unit,
    onError: (err: String) -> Unit,
    cryptoObject: BiometricPrompt.CryptoObject,
    confirmationRequired: Boolean = false,
): BiometricAuthCanceller {
    val biometricPrompt = BiometricPrompt(
        activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val o = result.cryptoObject
                if (o != null) {
                    onSuccess(o)
                } else {
                    onError("CryptoObject is null")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder().run {
        setTitle(title)
        setNegativeButtonText("Cancel")
        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        setConfirmationRequired(confirmationRequired)
        build()
    }
    biometricPrompt.authenticate(promptInfo, cryptoObject)
    return BiometricAuthCanceller(biometricPrompt)
}

class BiometricAuthCanceller(private val prompt: BiometricPrompt) {
    fun cancel() {
        prompt.cancelAuthentication()
    }
}

class BiometricAuthViewModel : ViewModel() {
    val showError = MutableViewModelStateFlow("")
}
