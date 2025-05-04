@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.xorkevin.multitool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import org.bouncycastle.bcpg.KeyIdentifier
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PGPDecryptTool() = ViewModelScope(arrayOf(PGPDecryptViewModel::class)) {
    val scrollState = rememberScrollState()
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()

    val inputSecretKey by pgpDecryptViewModel.inputSecretKey.collectAsStateWithLifecycle()
    val secretKeyRings by pgpDecryptViewModel.secretKeyRings.collectAsStateWithLifecycle(
        Result.failure(Exception("No secret keyring"))
    )
    val inputPassphrase by pgpDecryptViewModel.inputPassphrase.collectAsStateWithLifecycle()
    val inputCiphertext by pgpDecryptViewModel.inputCiphertext.collectAsStateWithLifecycle()

    val plaintext by pgpDecryptViewModel.plaintext.collectAsStateWithLifecycle(
        Result.failure(Exception("No secret keyring"))
    )

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        TextField(
            label = { Text(text = "ASCII armored secret key") },
            value = inputSecretKey,
            onValueChange = { pgpDecryptViewModel.updateInputSecretKey(it) },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Ciphertext") },
            value = inputCiphertext,
            onValueChange = { pgpDecryptViewModel.updateInputCiphertext(it) },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Passphrase") },
            value = inputPassphrase,
            onValueChange = { pgpDecryptViewModel.updateInputPassphrase(it) },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        secretKeyRings.onFailure {
            Text(
                text = "Invalid secret key: ${it.toString()}", modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
        plaintext.onFailure {
            Text(
                text = "Failed decrypting: ${it.toString()}", modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
        plaintext.onSuccess {
            Text(
                text = it, fontFamily = FontFamily.Monospace, modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}

class PGPDecryptViewModel : ViewModel() {
    private val _inputSecretKey = MutableStateFlow("")
    val inputSecretKey = _inputSecretKey.asStateFlow()
    fun updateInputSecretKey(value: String) {
        _inputSecretKey.update { value }
    }

    val secretKeyRings = inputSecretKey.mapLatest {
        delay(250.milliseconds)
        loadSecretKeys(it)
    }

    private val _inputPassphrase = MutableStateFlow("")
    val inputPassphrase = _inputPassphrase.asStateFlow()
    fun updateInputPassphrase(value: String) {
        _inputPassphrase.update { value }
    }

    private val _inputCiphertext = MutableStateFlow("")
    val inputCiphertext = _inputCiphertext.asStateFlow()
    fun updateInputCiphertext(value: String) {
        _inputCiphertext.update { value }
    }

    val plaintext = secretKeyRings.combine(inputPassphrase) { a, b -> Pair(a, b) }
        .combine(inputCiphertext) { (a, b), c -> Triple(a, b, c) }
        .mapLatest { (secretKeyRings, inputPassphrase, inputCiphertext) ->
            delay(250.milliseconds)
            val secKeyRings = secretKeyRings.getOrElse {
                return@mapLatest Result.failure(Exception("No secret keyring"))
            }
            decryptMessage(secKeyRings, inputPassphrase, inputCiphertext)
        }
}

internal fun decryptMessage(
    keyringCollection: BcPGPSecretKeyRingCollection,
    passphrase: String,
    ciphertext: String,
): Result<String> {
    val pgpObjFactory = try {
        BcPGPObjectFactory(PGPUtil.getDecoderStream(ByteArrayInputStream(ciphertext.toByteArray())))
    } catch (e: IOException) {
        return Result.failure(e)
    }
    return pgpObjFactory.firstNotNullOfOrNull {
        if (it !is PGPEncryptedDataList) {
            return@firstNotNullOfOrNull null
        }
        it.firstNotNullOfOrNull { encData ->
            if (encData !is PGPPublicKeyEncryptedData) {
                return@firstNotNullOfOrNull null
            }
            val secretKey = findSecretKey(keyringCollection, encData.keyIdentifier)
                ?: return@firstNotNullOfOrNull null
            try {
                val privateKey = secretKey.extractPrivateKey(
                    BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(
                        passphrase.toCharArray()
                    )
                )
                val dataStream = BcPGPObjectFactory(
                    encData.getDataStream(
                        BcPublicKeyDataDecryptorFactory(privateKey)
                    )
                )
                dataStream.firstNotNullOfOrNull {
                    if (it !is PGPLiteralData) {
                        return Result.failure(Exception("Unknown encrypted data packet"))
                    }
                    val out = ByteArrayOutputStream()
                    out.write(it.inputStream.readBytes())
                    if (!encData.isIntegrityProtected || !encData.verify()) {
                        return Result.failure(Exception("Message failed integrity check"))
                    }
                    Result.success(out.toString())
                }
            } catch (e: PGPException) {
                return Result.failure(e)
            } catch (e: IOException) {
                return Result.failure(e)
            }
        }
    } ?: return Result.failure(Exception("No encrypted data list in cipher text"))
}

private fun findSecretKey(
    keyringCollection: BcPGPSecretKeyRingCollection,
    identifier: KeyIdentifier,
): PGPSecretKey? {
    return keyringCollection.firstNotNullOfOrNull {
        it.getSecretKey(identifier)
    }
}

internal fun loadSecretKeys(armoredSecretKey: String): Result<BcPGPSecretKeyRingCollection> {
    return try {
        Result.success(
            BcPGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(
                    ByteArrayInputStream(
                        armoredSecretKey.toByteArray()
                    )
                )
            )
        )
    } catch (e: PGPException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(e)
    }
}
