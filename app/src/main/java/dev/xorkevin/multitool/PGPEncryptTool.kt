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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.internal.asn1.cryptlib.CryptlibObjectIdentifiers
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
fun PGPEncryptTool() = ViewModelScope(arrayOf(PGPEncryptViewModel::class)) {
    val scrollState = rememberScrollState()
    val pgpEncryptViewModel: PGPEncryptViewModel = scopedViewModel()

    val inputPublicKey by pgpEncryptViewModel.inputPublicKey.collectAsStateWithLifecycle()
    val inputPlaintext by pgpEncryptViewModel.inputPlaintext.collectAsStateWithLifecycle()
    val publicKey by pgpEncryptViewModel.publicKey.collectAsStateWithLifecycle(
        Result.failure(Exception("No public key"))
    )
    val ciphertext by pgpEncryptViewModel.ciphertext.collectAsStateWithLifecycle(
        Result.failure(Exception("No public key"))
    )

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        TextField(
            label = { Text(text = "ASCII armored public key") },
            value = inputPublicKey,
            onValueChange = { pgpEncryptViewModel.updateInputPublicKey(it) },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Plaintext") },
            value = inputPlaintext,
            onValueChange = { pgpEncryptViewModel.updateInputPlaintext(it) },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        publicKey.onFailure {
            Text(
                text = "Invalid public key: ${it.toString()}", modifier = Modifier
                    .padding(16.dp, 8.dp)
                    .fillMaxWidth()
            )
        }
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
}

class PGPEncryptViewModel : ViewModel() {
    private val _inputPublicKey = MutableStateFlow("")
    val inputPublicKey = _inputPublicKey.asStateFlow()
    fun updateInputPublicKey(value: String) {
        _inputPublicKey.update { value }
    }

    val publicKey = inputPublicKey.mapLatest {
        delay(250.milliseconds)
        loadPublicKey(it)
    }

    private val _inputPlaintext = MutableStateFlow("")
    val inputPlaintext = _inputPlaintext.asStateFlow()
    fun updateInputPlaintext(value: String) {
        _inputPlaintext.update { value }
    }

    val ciphertext = publicKey.combine(inputPlaintext) { a, b -> Pair(a, b) }
        .mapLatest { (publicKey, inputPlaintext) ->
            delay(250.milliseconds)
            val pubKey = publicKey.getOrElse {
                return@mapLatest Result.failure(Exception("No public key"))
            }
            encryptMessage(pubKey, inputPlaintext)
        }

    init {
        viewModelScope.launch {

        }
    }
}

internal fun encryptMessage(publicKey: PGPPublicKey, message: String): Result<String> {
    CryptlibObjectIdentifiers.cryptlib.toString()
    val plaintext = message.toByteArray()
    val literalData = ByteArrayOutputStream()
    PGPLiteralDataGenerator().open(
        literalData,
        PGPLiteralDataGenerator.UTF8,
        "",
        plaintext.size.toLong(),
        Date()
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
            PGPUtil.getDecoderStream(
                ByteArrayInputStream(
                    armoredPublicKey.toByteArray()
                )
            )
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

