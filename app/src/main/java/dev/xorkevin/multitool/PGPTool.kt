package dev.xorkevin.multitool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.KeyIdentifier
import org.bouncycastle.internal.asn1.cryptlib.CryptlibObjectIdentifiers
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRingCollection
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds


@Composable
fun PGPTool() {
    var pubKeyInp by remember { mutableStateOf("") }
    var plainTextInp by remember { mutableStateOf("") }
    var secKeyInp by remember { mutableStateOf("") }
    var passphraseInp by remember { mutableStateOf("") }
    var cipherTextInp by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    var publicKey by remember { mutableStateOf<Result<PGPPublicKey>>(Result.failure(Exception("No key"))) }
    LaunchedEffect(pubKeyInp) {
        delay(250.milliseconds)
        publicKey = loadPublicKey(pubKeyInp)
    }

    var ciphertext by remember { mutableStateOf<Result<String>>(Result.failure(Exception("No public key"))) }
    LaunchedEffect(publicKey, plainTextInp) {
        delay(250.milliseconds)
        val pubKey = publicKey.getOrElse {
            ciphertext = Result.failure(Exception("No public key"))
            return@LaunchedEffect
        }
        ciphertext = encryptMessage(pubKey, plainTextInp)
    }

    var secretKeyrings by remember {
        mutableStateOf<Result<BcPGPSecretKeyRingCollection>>(
            Result.failure(
                Exception("No secret keyring")
            )
        )
    }
    LaunchedEffect(secKeyInp) {
        delay(250.milliseconds)
        secretKeyrings = loadSecretKeys(secKeyInp)
    }

    var plaintext by remember { mutableStateOf<Result<String>>(Result.failure(Exception("No secret keyring"))) }
    LaunchedEffect(secretKeyrings, cipherTextInp, passphraseInp) {
        delay(250.milliseconds)
        val secKeyrings = secretKeyrings.getOrElse {
            plaintext = Result.failure(Exception("No secret keyring"))
            return@LaunchedEffect
        }
        plaintext = decryptMessage(secKeyrings, passphraseInp, cipherTextInp)
    }

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Text(
            text = "Encrypt", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "ASCII armored public key") },
            value = pubKeyInp,
            onValueChange = { pubKeyInp = it },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Plain text") },
            value = plainTextInp,
            onValueChange = { plainTextInp = it },
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
        Text(
            text = "Decrypt", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "ASCII armored secret key") },
            value = secKeyInp,
            onValueChange = { secKeyInp = it },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Cipher text") },
            value = cipherTextInp,
            onValueChange = { cipherTextInp = it },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        TextField(
            label = { Text(text = "Passphrase") },
            value = passphraseInp,
            onValueChange = { passphraseInp = it },
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        )
        secretKeyrings.onFailure {
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
