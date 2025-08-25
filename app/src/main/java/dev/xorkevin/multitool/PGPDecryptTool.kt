@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package dev.xorkevin.multitool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
fun PGPDecryptTool() = ViewModelScope(PGPDecryptViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        PGPDecryptSecretKeyInput()
        PGPDecryptCiphertextInput()
        PGPDecryptPassphraseInput()
        PGPDecryptSecretKeyDisplay()
        PGPDecryptPlaintextDisplay()
    }
}

@Composable
fun PGPDecryptSecretKeyInput() {
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()
    var inputSecretKey by pgpDecryptViewModel.inputSecretKey.collectAsStateWithLifecycle()
    TextField(
        label = { Text(text = "ASCII armored secret key") },
        value = inputSecretKey,
        onValueChange = { inputSecretKey = it },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { inputSecretKey = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    )
}

@Composable
fun PGPDecryptCiphertextInput() {
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()
    var inputCiphertext by pgpDecryptViewModel.inputCiphertext.collectAsStateWithLifecycle()
    TextField(
        label = { Text(text = "Ciphertext") },
        value = inputCiphertext,
        onValueChange = { inputCiphertext = it },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { inputCiphertext = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    )
}

@Composable
fun PGPDecryptPassphraseInput() {
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()
    var inputPassphrase by pgpDecryptViewModel.inputPassphrase.collectAsStateWithLifecycle()
    TextField(
        label = { Text(text = "Passphrase") },
        value = inputPassphrase,
        onValueChange = { inputPassphrase = it },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { inputPassphrase = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    )
}

@Composable
fun PGPDecryptSecretKeyDisplay() {
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()
    val secretKeyRings by pgpDecryptViewModel.secretKeyRings.collectAsStateWithLifecycle()
    secretKeyRings.onFailure {
        Text(
            text = "Invalid secret key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun PGPDecryptPlaintextDisplay() {
    val pgpDecryptViewModel: PGPDecryptViewModel = scopedViewModel()
    val plaintext by pgpDecryptViewModel.plaintext.collectAsStateWithLifecycle()
    plaintext.onFailure {
        Text(
            text = "Failed decrypting: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    plaintext.onSuccess {
        Text(
            text = it,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

class PGPDecryptViewModel : ViewModel() {
    val inputSecretKey = MutableViewModelStateFlow("")
    val secretKeyRings = inputSecretKey.flow.mapLatest {
        delay(250.milliseconds)
        withContext(Dispatchers.Default) {
            loadSecretKeys(it)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        Result.failure(Exception("No secret keyring"))
    )
    val inputPassphrase = MutableViewModelStateFlow("")
    val inputCiphertext = MutableViewModelStateFlow("")
    val plaintext = combine(
        secretKeyRings,
        inputPassphrase.flow.debounce(250.milliseconds),
        inputCiphertext.flow.debounce(250.milliseconds),
    ) { a, b, c ->
        Triple(a, b, c)
    }.mapLatest { (secretKeyRings, inputPassphrase, inputCiphertext) ->
        delay(250.milliseconds)
        val secKeyRings = secretKeyRings.getOrElse {
            return@mapLatest Result.failure(Exception("No secret keyring"))
        }
        withContext(Dispatchers.Default) {
            decryptMessage(secKeyRings, inputPassphrase, inputCiphertext)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        Result.failure(Exception("No secret keyring"))
    )
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
                    BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(passphrase.toCharArray())
                )
                val dataStream = BcPGPObjectFactory(
                    encData.getDataStream(BcPublicKeyDataDecryptorFactory(privateKey)),
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
): PGPSecretKey? =
    keyringCollection.firstNotNullOfOrNull { it.getSecretKey(identifier) }

internal fun loadSecretKeys(armoredSecretKey: String): Result<BcPGPSecretKeyRingCollection> {
    return try {
        Result.success(
            BcPGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(ByteArrayInputStream(armoredSecretKey.toByteArray())),
            )
        )
    } catch (e: PGPException) {
        Result.failure(e)
    } catch (e: IOException) {
        Result.failure(e)
    }
}
