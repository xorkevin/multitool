@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.xorkevin.multitool

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.util.security.SecurityUtils
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

class KeyStoreService(appContext: Context) {
    private val androidKeyStoreMutex = Mutex()
    private var androidKeyStore: KeyStore? = null
    private suspend fun getAndroidKeyStore(): Result<KeyStore> {
        val aks = androidKeyStore
        if (aks != null) {
            return Result.success(aks)
        }
        androidKeyStoreMutex.withLock {
            val aks = androidKeyStore
            if (aks != null) {
                return Result.success(aks)
            }
            try {
                val aks = KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                }
                androidKeyStore = aks
                return Result.success(aks)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
    }

    private fun generateAndroidKeyStoreKey(name: String): Result<Unit> {
        try {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    name, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).run {
                    setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    setKeySize(256)
                    setInvalidatedByBiometricEnrollment(true)
                    setRandomizedEncryptionRequired(true)
                    setUnlockedDeviceRequired(true)
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    setUserAuthenticationRequired(true)
                    build()
                })
            keyGenerator.generateKey()
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun deleteAndroidKeyStoreKey(name: String): Result<Unit> {
        val keyStore = getAndroidKeyStore().getOrElse { return Result.failure(it) }
        return try {
            if (keyStore.containsAlias(name)) {
                keyStore.deleteEntry(name)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAndroidKeyStoreKeyCipher(
        name: String,
        mode: Int,
        nonce: ByteArray? = null,
    ): Result<Cipher> {
        val keyStore = getAndroidKeyStore().getOrElse { return Result.failure(it) }
        return try {
            if (!keyStore.containsAlias(name)) {
                withContext(Dispatchers.Default) {
                    generateAndroidKeyStoreKey(name)
                }.getOrElse { return Result.failure(it) }
            }
            val sk = keyStore.getKey(name, null) as SecretKey
            Result.success(Cipher.getInstance("AES/GCM/NoPadding").apply {
                if (mode == Cipher.DECRYPT_MODE) {
                    init(mode, sk, GCMParameterSpec(128, nonce))
                } else {
                    init(mode, sk)
                }
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val ROOT_KEY_NAME = "root_key"
    private val rootKeyMutex = Mutex()
    private val rootKeyState = MutableStateFlow<ByteArray?>(null)

    val unlockState = rootKeyState.mapLatest { it != null }
    private val _setupState = MutableStateFlow(false)
    val setupState = _setupState.asStateFlow()
    private val _biometricState = MutableStateFlow(false)
    val biometricState = _biometricState.asStateFlow()

    suspend fun refreshState(): Result<Unit> {
        rootKeyMutex.withLock {
            val encKey = withContext(Dispatchers.IO) {
                try {
                    Result.success(keyDB.rootKeyDao().getByName(ROOT_KEY_NAME))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            if (encKey == null) {
                _setupState.update { false }
                _biometricState.update { false }
                return Result.success(Unit)
            }
            _setupState.update { true }
            _biometricState.update { encKey.encRootKey != "" }
            return Result.success(Unit)
        }
    }

    private val base64URLRaw = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    suspend fun lock() {
        rootKeyMutex.withLock {
            rootKeyState.update { null }
        }
    }

    suspend fun biometricUnlock(activityCtx: Context): Result<Unit> {
        rootKeyState.value?.let {
            return Result.success(Unit)
        }

        val activity: FragmentActivity? = activityCtx.getActivity()
        if (activity == null) {
            return Result.failure(Exception("No activity"))
        }

        rootKeyMutex.withLock {
            rootKeyState.value?.let {
                return Result.success(Unit)
            }

            val encKey = withContext(Dispatchers.IO) {
                try {
                    Result.success(keyDB.rootKeyDao().getByName(ROOT_KEY_NAME))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) } ?: return Result.failure(
                Exception("No root key")
            )

            if (encKey.encRootKey == "") {
                return Result.failure(Exception("Biometric unlock disabled"))
            }

            val encKeyHashBytes = try {
                base64URLRaw.decode(encKey.keyHash)
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val (encKeyBytes, nonce) = try {
                val split = encKey.encRootKey.split("$", limit = 2)
                if (split.size != 2) {
                    return Result.failure(Exception("Malformed root key"))
                }
                base64URLRaw.decode(split[1]) to base64URLRaw.decode(split[0])
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val lockedCipher = getAndroidKeyStoreKeyCipher(
                ROOT_KEY_NAME, Cipher.DECRYPT_MODE, nonce,
            ).getOrElse { return Result.failure(it) }

            val cipher = suspendCancellableCoroutine { continuation ->
                val canceller = authWithBiometricCrypto(
                    "Unlock vault",
                    activity,
                    onSuccess = { continuation.resume(Result.success(it)) },
                    onError = { continuation.resume(Result.failure(Exception(it))) },
                    cryptoObject = BiometricPrompt.CryptoObject(lockedCipher)
                )
                continuation.invokeOnCancellation {
                    canceller.cancel()
                }
            }.getOrElse { return Result.failure(it) }.cipher
                ?: return Result.failure(Exception("No cipher"))

            val key = withContext(Dispatchers.Default) {
                try {
                    Result.success(cipher.doFinal(encKeyBytes))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }

            val keyHash = withContext(Dispatchers.Default) {
                CryptoUtil.blake2b(key, 32)
            }.getOrElse { return Result.failure(it) }
            if (!MessageDigest.isEqual(encKeyHashBytes, keyHash)) {
                return Result.failure(Exception("Invalid key"))
            }

            rootKeyState.update { key }
            return Result.success(Unit)
        }
    }

    suspend fun passwordUnlock(password: ByteArray): Result<Unit> {
        rootKeyState.value?.let {
            return Result.success(Unit)
        }

        rootKeyMutex.withLock {
            rootKeyState.value?.let {
                return Result.success(Unit)
            }

            val encKey = withContext(Dispatchers.IO) {
                try {
                    Result.success(keyDB.rootKeyDao().getByName(ROOT_KEY_NAME))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) } ?: return Result.failure(
                Exception("No root key")
            )

            val kdfParams: KDFParams = try {
                Json.decodeFromString(encKey.kdfParams)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            val salt = try {
                base64URLRaw.decode(kdfParams.salt)
            } catch (e: Exception) {
                return Result.failure(e)
            }
            val encKeyHashBytes = try {
                base64URLRaw.decode(encKey.keyHash)
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val key = withContext(Dispatchers.Default) {
                CryptoUtil.argon2id(
                    password, salt, kdfParams.mem, kdfParams.iter, kdfParams.par, kdfParams.length
                )
            }.getOrElse { return Result.failure(it) }

            val keyHash = withContext(Dispatchers.Default) {
                CryptoUtil.blake2b(key, 32)
            }.getOrElse { return Result.failure(it) }
            if (!MessageDigest.isEqual(encKeyHashBytes, keyHash)) {
                return Result.failure(Exception("Invalid password"))
            }

            rootKeyState.update { key }
            return Result.success(Unit)
        }
    }

    @Serializable
    data class KDFParams(
        val kind: String,
        val salt: String,
        val mem: Int,
        val iter: Int,
        val par: Int,
        val length: Int
    )

    suspend fun generateRootKey(password: ByteArray): Result<Unit> {
        rootKeyState.value?.let {
            return Result.success(Unit)
        }

        rootKeyMutex.withLock {
            rootKeyState.value?.let {
                return Result.success(Unit)
            }

            val existingKey = withContext(Dispatchers.IO) {
                try {
                    Result.success(keyDB.rootKeyDao().getByName(ROOT_KEY_NAME))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            if (existingKey != null) {
                return Result.success(Unit)
            }

            val salt = ByteArray(32)
            val key = withContext(Dispatchers.Default) {
                SecureRandom.getInstanceStrong().nextBytes(salt)
                CryptoUtil.argon2id(password, salt, 19456, 2, 1, 32)
            }.getOrElse { return Result.failure(it) }
            val paramsStr = Json.encodeToString(
                KDFParams(
                    "argon2id19", base64URLRaw.encode(salt), 19456, 2, 1, 32
                )
            )

            val keyHash = base64URLRaw.encode(withContext(Dispatchers.Default) {
                CryptoUtil.blake2b(key, 32)
            }.getOrElse { return Result.failure(it) })

            return withContext(Dispatchers.IO) {
                try {
                    keyDB.rootKeyDao().insertAll(RootKey(ROOT_KEY_NAME, keyHash, paramsStr, ""))
                    _setupState.update { true }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun deleteRootKey(): Result<Unit> {
        rootKeyMutex.withLock {
            rootKeyState.update { null }

            withContext(Dispatchers.IO) {
                try {
                    keyDB.rootKeyDao().deleteByName(ROOT_KEY_NAME)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            _setupState.update { false }
            _biometricState.update { false }
            deleteAndroidKeyStoreKey(ROOT_KEY_NAME).getOrElse { return Result.failure(it) }
            return Result.success(Unit)
        }
    }

    suspend fun setupBiometric(activityCtx: Context): Result<Unit> {
        val activity: FragmentActivity? = activityCtx.getActivity()
        if (activity == null) {
            return Result.failure(Exception("No activity"))
        }

        rootKeyMutex.withLock {
            val key = rootKeyState.value
            if (key == null) {
                return Result.failure(Exception("Vault is locked"))
            }

            val encKey = withContext(Dispatchers.IO) {
                try {
                    Result.success(keyDB.rootKeyDao().getByName(ROOT_KEY_NAME))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) } ?: return Result.failure(
                Exception("No root key")
            )

            if (encKey.encRootKey != "") {
                return Result.success(Unit)
            }

            val encKeyHashBytes = try {
                base64URLRaw.decode(encKey.keyHash)
            } catch (e: Exception) {
                return Result.failure(e)
            }

            val keyHash = withContext(Dispatchers.Default) {
                CryptoUtil.blake2b(key, 32)
            }.getOrElse { return Result.failure(it) }
            if (!MessageDigest.isEqual(encKeyHashBytes, keyHash)) {
                return Result.failure(Exception("Invalid key"))
            }

            val lockedCipher = getAndroidKeyStoreKeyCipher(
                ROOT_KEY_NAME, Cipher.ENCRYPT_MODE
            ).getOrElse { return Result.failure(it) }

            val cipher = suspendCancellableCoroutine { continuation ->
                val canceller = authWithBiometricCrypto(
                    "Setup biometric vault unlock",
                    activity,
                    onSuccess = { continuation.resume(Result.success(it)) },
                    onError = { continuation.resume(Result.failure(Exception(it))) },
                    cryptoObject = BiometricPrompt.CryptoObject(lockedCipher),
                    confirmationRequired = true,
                )
                continuation.invokeOnCancellation {
                    canceller.cancel()
                }
            }.getOrElse { return Result.failure(it) }.cipher
                ?: return Result.failure(Exception("No cipher"))

            val encRootKeyBytes = withContext(Dispatchers.Default) {
                try {
                    Result.success(cipher.doFinal(key))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            val encRootKey =
                "${base64URLRaw.encode(cipher.iv)}$${base64URLRaw.encode(encRootKeyBytes)}"

            return withContext(Dispatchers.IO) {
                try {
                    keyDB.rootKeyDao().setupBiometric(ROOT_KEY_NAME, encRootKey)
                    _biometricState.update { true }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun removeBiometric(): Result<Unit> {
        rootKeyMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    keyDB.rootKeyDao().removeBiometric(ROOT_KEY_NAME)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            _biometricState.update { false }
            deleteAndroidKeyStoreKey(ROOT_KEY_NAME).getOrElse { return Result.failure(it) }
            return Result.success(Unit)
        }
    }

    suspend fun getAllSshKeys(): Result<List<SshKeyNameTuple>> {
        return withContext(Dispatchers.IO) {
            try {
                val res = keyDB.sshKeyDao().getAll()
                Result.success(res)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteSshKey(name: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                keyDB.sshKeyDao().deleteByName(name)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun storeSshKey(name: String, keyStr: String, passphrase: String): Result<Unit> {
        val rootKey = rootKeyState.value
        if (rootKey == null) {
            return Result.failure(Exception("Vault is locked"))
        }

        withContext(Dispatchers.Default) {
            loadSSHPrivateKey(keyStr, passphrase)
        }.getOrElse { return Result.failure(it) }

        val encKey = base64URLRaw.encode(withContext(Dispatchers.Default) {
            val nonce = ByteArray(CryptoUtil.XCHACHA20_POLY1305_NONCE_SIZE)
            SecureRandom.getInstanceStrong().nextBytes(nonce)
            CryptoUtil.encryptXChaCha20Poly1305(rootKey, nonce, keyStr.toByteArray())
        }.getOrElse { return Result.failure(it) })
        val encPassphrase = base64URLRaw.encode(withContext(Dispatchers.Default) {
            val nonce = ByteArray(CryptoUtil.XCHACHA20_POLY1305_NONCE_SIZE)
            SecureRandom.getInstanceStrong().nextBytes(nonce)
            CryptoUtil.encryptXChaCha20Poly1305(rootKey, nonce, passphrase.toByteArray())
        }.getOrElse { return Result.failure(it) })

        return withContext(Dispatchers.IO) {
            try {
                keyDB.sshKeyDao().insertAll(
                    SshKey(name = name, encKeyStr = encKey, encPassphrase = encPassphrase)
                )
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                val res = Result.failure<Unit>(e)
                return@withContext res
            }
        }
    }

    suspend fun getSshKey(name: String): Result<KeyPair> {
        val rootKey = rootKeyState.value
        if (rootKey == null) {
            return Result.failure(Exception("Vault is locked"))
        }
        val key = withContext(Dispatchers.IO) {
            try {
                return@withContext Result.success(keyDB.sshKeyDao().getByName(name))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }.getOrElse { return Result.failure(it) }
        if (key == null) {
            return Result.failure(Exception("No key"))
        }
        val encKeyBytes = try {
            base64URLRaw.decode(key.encKeyStr)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val encPassphraseBytes = try {
            base64URLRaw.decode(key.encPassphrase)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return withContext(Dispatchers.Default) {
            val keyStr = CryptoUtil.decryptXChaCha20Poly1305(rootKey, encKeyBytes)
                .getOrElse { return@withContext Result.failure(it) }.decodeToString()
            val passphrase = CryptoUtil.decryptXChaCha20Poly1305(rootKey, encPassphraseBytes)
                .getOrElse { return@withContext Result.failure(it) }.decodeToString()
            loadSSHPrivateKey(keyStr, passphrase)
        }
    }

    private val keyDB = Room.databaseBuilder(
        appContext, DB::class.java, "keystore-db"
    ).build()

    @Database(entities = [RootKey::class, SshKey::class], version = 1)
    abstract class DB : RoomDatabase() {
        abstract fun rootKeyDao(): RootKeyDao
        abstract fun sshKeyDao(): SshKeyDao
    }

    @Entity(tableName = "keystore_root_keys", primaryKeys = ["name"])
    data class RootKey(
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "key_hash") val keyHash: String,
        @ColumnInfo(name = "kdf_params") val kdfParams: String,
        @ColumnInfo(name = "enc_root_key") val encRootKey: String,
    )

    @Dao
    interface RootKeyDao {
        @Query("SELECT * FROM keystore_root_keys WHERE name = :name")
        suspend fun getByName(name: String): RootKey?

        @Insert
        suspend fun insertAll(vararg rootKeys: RootKey)

        @Query("DELETE FROM keystore_root_keys WHERE name = :name")
        suspend fun deleteByName(name: String): Int

        @Query("UPDATE keystore_root_keys SET enc_root_key = :encRootKey WHERE name = :name")
        suspend fun setupBiometric(name: String, encRootKey: String): Int

        @Query("UPDATE keystore_root_keys SET enc_root_key = '' WHERE name = :name")
        suspend fun removeBiometric(name: String): Int
    }

    @Entity(tableName = "keystore_ssh_keys", primaryKeys = ["name"])
    data class SshKey(
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "enc_key_str") val encKeyStr: String,
        @ColumnInfo(name = "enc_passphrase") val encPassphrase: String,
    )

    data class SshKeyNameTuple(
        @ColumnInfo(name = "name") val name: String,
    )

    @Dao
    interface SshKeyDao {
        @Query("SELECT * FROM keystore_ssh_keys WHERE name = :name")
        suspend fun getByName(name: String): SshKey?

        @Query("SELECT name FROM keystore_ssh_keys ORDER BY name")
        suspend fun getAll(): List<SshKeyNameTuple>

        @Insert
        suspend fun insertAll(vararg sshKeys: SshKey)

        @Query("DELETE FROM keystore_ssh_keys WHERE name = :name")
        suspend fun deleteByName(name: String): Int
    }
}

internal fun loadSSHPrivateKey(secretKey: String, passphrase: String): Result<KeyPair> {
    val keypairs = try {
        SecurityUtils.loadKeyPairIdentities(
            null,
            null,
            secretKey.byteInputStream(),
            FilePasswordProvider.of(passphrase),
        )
    } catch (e: IOException) {
        return Result.failure(e)
    } catch (e: GeneralSecurityException) {
        return Result.failure(e)
    }
    if (keypairs == null) {
        return Result.failure(Exception("No keys"))
    }
    val iter = keypairs.iterator()
    if (!iter.hasNext()) {
        return Result.failure(Exception("No keys"))
    }
    val keypair = iter.next()
    if (keypair == null) {
        return Result.failure(Exception("No keys"))
    }
    return Result.success(keypair)
}
