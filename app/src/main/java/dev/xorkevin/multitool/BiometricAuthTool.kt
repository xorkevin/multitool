package dev.xorkevin.multitool

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

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
