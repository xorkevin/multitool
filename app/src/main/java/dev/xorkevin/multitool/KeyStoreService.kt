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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
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

    private val base64URLRaw = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    suspend fun lock() {
        rootKeyMutex.withLock {
            rootKeyState.update { null }
        }
    }

    suspend fun unlock(activityCtx: Context): Result<Unit> {
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
                    "Unlock Vault",
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
            rootKeyState.update { key }
            return Result.success(Unit)
        }
    }

    suspend fun setRootKey(key: ByteArray) {
        rootKeyMutex.withLock {
            rootKeyState.update { key }
        }
    }

    suspend fun encryptRootKey(key: ByteArray, activityCtx: Context): Result<Unit> {
        val activity: FragmentActivity? = activityCtx.getActivity()
        if (activity == null) {
            return Result.failure(Exception("No activity"))
        }

        rootKeyMutex.withLock {
            rootKeyState.update { null }

            val lockedCipher = getAndroidKeyStoreKeyCipher(
                ROOT_KEY_NAME, Cipher.ENCRYPT_MODE
            ).getOrElse { return Result.failure(it) }

            val cipher = suspendCancellableCoroutine { continuation ->
                val canceller = authWithBiometricCrypto(
                    "Unlock Vault",
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

            val encKeyBytes = withContext(Dispatchers.Default) {
                try {
                    Result.success(cipher.doFinal(key))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }.getOrElse { return Result.failure(it) }
            val encKey = "${base64URLRaw.encode(cipher.iv)}$${base64URLRaw.encode(encKeyBytes)}"

            return withContext(Dispatchers.IO) {
                try {
                    keyDB.rootKeyDao().insertAll(RootKey(ROOT_KEY_NAME, encKey))
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
            deleteAndroidKeyStoreKey(ROOT_KEY_NAME).getOrElse { return Result.failure(it) }
            return Result.success(Unit)
        }
    }

    fun getSshKeyDao(): SshKeyDao {
        return keyDB.sshKeyDao()
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
    }

    @Entity(tableName = "keystore_ssh_keys", primaryKeys = ["name"])
    data class SshKey(
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "enc_key_str") val encKeyStr: String,
        @ColumnInfo(name = "enc_passphrase") val encPassphrase: String,
    )

    data class SshKeyNameTuple(
        @ColumnInfo(name = "name") val name: String?,
    )

    @Dao
    interface SshKeyDao {
        @Query("SELECT * FROM keystore_ssh_keys WHERE name = :name")
        suspend fun getByName(name: String): SshKey?

        @Query("SELECT name FROM keystore_ssh_keys")
        suspend fun getAll(): List<SshKeyNameTuple>

        @Insert
        suspend fun insertAll(vararg sshKeys: SshKey)

        @Query("DELETE FROM keystore_ssh_keys WHERE name = :name")
        suspend fun deleteByName(name: String): Int
    }
}
