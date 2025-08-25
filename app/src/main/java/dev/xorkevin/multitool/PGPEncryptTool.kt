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
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun PGPEncryptTool() = ViewModelScope(PGPEncryptViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        PGPEncryptPublicKeyInput()
        PGPEncryptPlaintextInput()
        PGPEncryptPublicKeyDisplay()
        PGPEncryptCiphertextDisplay()
    }
}

@Composable
fun PGPEncryptPublicKeyInput() {
    val pgpEncryptViewModel: PGPEncryptViewModel = scopedViewModel()
    var inputPublicKey by pgpEncryptViewModel.inputPublicKey.collectAsStateWithLifecycle()
    TextField(
        label = { Text(text = "ASCII armored public key") },
        value = inputPublicKey,
        onValueChange = { inputPublicKey = it },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { inputPublicKey = it ?: "" },
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
fun PGPEncryptPlaintextInput() {
    val pgpEncryptViewModel: PGPEncryptViewModel = scopedViewModel()
    var inputPlaintext by pgpEncryptViewModel.inputPlaintext.collectAsStateWithLifecycle()
    TextField(
        label = { Text(text = "Plaintext") },
        value = inputPlaintext,
        onValueChange = { inputPlaintext = it },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { inputPlaintext = it ?: "" },
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
fun PGPEncryptPublicKeyDisplay() {
    val pgpEncryptViewModel: PGPEncryptViewModel = scopedViewModel()
    val publicKey by pgpEncryptViewModel.publicKey.collectAsStateWithLifecycle()
    publicKey.onFailure {
        Text(
            text = "Invalid public key: ${it.toString()}", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
fun PGPEncryptCiphertextDisplay() {
    val pgpEncryptViewModel: PGPEncryptViewModel = scopedViewModel()
    val ciphertext by pgpEncryptViewModel.ciphertext.collectAsStateWithLifecycle()
    ciphertext.onFailure {
        Text(
            text = "Failed encrypting: ${it.toString()}", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    ciphertext.onSuccess {
        Text(
            text = it, fontFamily = FontFamily.Monospace, modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
}

class PGPEncryptViewModel : ViewModel() {
    val inputPublicKey = MutableViewModelStateFlow("")
    val publicKey = inputPublicKey.flow.mapLatest {
        delay(250.milliseconds)
        withContext(Dispatchers.Default) {
            loadPublicKey(it)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        Result.failure(Exception("No public key"))
    )
    val inputPlaintext = MutableViewModelStateFlow("")
    val ciphertext = combine(publicKey, inputPlaintext.flow.debounce(250.milliseconds)) { a, b ->
        Pair(a, b)
    }.mapLatest { (publicKey, inputPlaintext) ->
        delay(250.milliseconds)
        val pubKey = publicKey.getOrElse {
            return@mapLatest Result.failure(Exception("No public key"))
        }
        withContext(Dispatchers.Default) {
            encryptMessage(pubKey, inputPlaintext)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(),
        Result.failure(Exception("No public key"))
    )
}

internal fun encryptMessage(publicKey: PGPPublicKey, message: String): Result<String> {
    val plaintext = message.toByteArray()
    val literalData = ByteArrayOutputStream()
    PGPLiteralDataGenerator().open(
        literalData, PGPLiteralDataGenerator.UTF8, "", plaintext.size.toLong(), Date()
    ).use {
        it.write(plaintext)
    }
    val literalDataBytes = literalData.toByteArray()
    val encryptor =
        PGPEncryptedDataGenerator(BcPGPDataEncryptorBuilder(PGPEncryptedDataGenerator.AES_256))
    encryptor.addMethod(BcPublicKeyKeyEncryptionMethodGenerator(publicKey))
    val out = ByteArrayOutputStream()
    ArmoredOutputStream(out).use {
        encryptor.open(it, literalDataBytes.size.toLong()).use {
            it.write(literalDataBytes)
        }
    }
    return Result.success(out.toString())
}

internal fun loadPublicKey(armoredPublicKey: String): Result<PGPPublicKey> {
    val keyringCollection = try {
        BcPGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armoredPublicKey.toByteArray()))
        )
    } catch (e: PGPException) {
        return Result.failure(e)
    } catch (e: IOException) {
        return Result.failure(e)
    }
    // encrypt with the first key available
    keyringCollection.keyRings.forEach {
        it.publicKeys.forEach {
            if (it.isEncryptionKey()) {
                return Result.success(it)
            }
        }
    }
    return Result.failure(Exception("No encryption key found"))
}
