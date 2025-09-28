package dev.xorkevin.multitool

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.util.security.SecurityUtils
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.KeyPair

@Composable
fun GitTool() = ViewModelScope(GitViewModel::class) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.verticalScroll(scrollState)) {
        GitCloneInput()
        SshKeyManagerInput()
    }
}

@Composable
fun GitCloneInput() {
    val gitViewModel: GitViewModel = scopedViewModel(
        factory = GitViewModel.Factory(LocalContext.current.applicationContext)
    )
    var name by gitViewModel.gitRepoName.collectAsStateWithLifecycle()
    var url by gitViewModel.gitRepoUrl.collectAsStateWithLifecycle()
    TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(text = "Name") },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(text = "URL") },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { url = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
}

@Composable
fun SshKeyManagerInput() {
    val coroutineScope = rememberCoroutineScope()
    val gitViewModel: GitViewModel = scopedViewModel(
        factory = GitViewModel.Factory(LocalContext.current.applicationContext)
    )
    var name by gitViewModel.sshKeyName.collectAsStateWithLifecycle()
    var keyStr by gitViewModel.sshKeyStr.collectAsStateWithLifecycle()
    var passphrase by gitViewModel.sshKeyPassphrase.collectAsStateWithLifecycle()
    val storeRes by gitViewModel.storeSshKeyRes.collectAsStateWithLifecycle()
    val getAllRes by gitViewModel.getAllKeysRes.collectAsStateWithLifecycle()
    val deleteRes by gitViewModel.deleteSshKeyRes.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        gitViewModel.getAllKeys()
    }

    TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(text = "Name") },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = keyStr,
        onValueChange = { keyStr = it },
        label = { Text(text = "Key") },
        trailingIcon = {
            QRScannerLauncher(
                onScan = { keyStr = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan key"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    TextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text(text = "Passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            QRScannerLauncher(
                onScan = { passphrase = it ?: "" },
                modifier = Modifier.padding(16.dp, 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add, contentDescription = "Scan passphrase"
                )
            }
        },
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
    )
    Button(
        onClick = {
            coroutineScope.launch {
                gitViewModel.storeSshKey().onSuccess { gitViewModel.getAllKeys() }
            }
        }, modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Store key")
    }
    storeRes.onFailure {
        Text(
            text = "Failed to store key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    Button(
        onClick = { coroutineScope.launch { gitViewModel.getAllKeys() } },
        modifier = Modifier
            .padding(16.dp, 8.dp)
            .fillMaxWidth()
    ) {
        Text(text = "Refresh")
    }
    getAllRes.onFailure {
        Text(
            text = "Failed to get keys: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    deleteRes.onFailure {
        Text(
            text = "Failed to delete key: ${it.toString()}",
            modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
    }
    getAllRes.onSuccess { keys ->
        Text(
            text = "${keys.size} Keys", modifier = Modifier
                .padding(16.dp, 8.dp)
                .fillMaxWidth()
        )
        keys.forEach {
            Row(modifier = Modifier.padding(16.dp, 8.dp)) {
                Text(
                    text = it, modifier = Modifier
                        .padding(8.dp, 0.dp)
                        .weight(1f)
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            gitViewModel.deleteSshKey(it).onSuccess { gitViewModel.getAllKeys() }
                        }
                    },
                    modifier = Modifier.padding(8.dp, 0.dp),
                ) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

class GitViewModel(private val appContext: Context) : ViewModel() {
    @Entity(tableName = "gitviewmodel_ssh_keys", primaryKeys = ["name"])
    data class SshKey(
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "key_str") val keyStr: String,
        @ColumnInfo(name = "passphrase") val passphrase: String,
    )

    data class SshKeyNameTuple(
        @ColumnInfo(name = "name") val name: String?,
    )

    @Dao
    interface KeyDao {
        @Query("SELECT * FROM gitviewmodel_ssh_keys WHERE name = :name")
        suspend fun getByName(name: String): SshKey?

        @Query("SELECT name FROM gitviewmodel_ssh_keys")
        suspend fun getAll(): List<SshKeyNameTuple>

        @Insert
        suspend fun insertAll(vararg sshKeys: SshKey)

        @Query("DELETE FROM gitviewmodel_ssh_keys WHERE name = :name")
        suspend fun deleteByName(name: String): Int
    }

    @Database(entities = [SshKey::class], version = 1)
    abstract class DB : RoomDatabase() {
        abstract fun keyDao(): KeyDao

        companion object {
            private val instanceLock = Mutex()
            private var instance: DB? = null

            suspend fun getInstance(appContext: Context): DB {
                instance?.let { return it }
                instanceLock.withLock {
                    var i = instance
                    if (i != null) {
                        return i
                    }
                    i = Room.databaseBuilder(
                        appContext, DB::class.java, "gitviewmodel-db"
                    ).build()
                    instance = i
                    return i
                }
            }
        }
    }

    private suspend fun loadSshKey(name: String): Result<KeyPair> {
        val db = DB.getInstance(appContext)
        val key = withContext(Dispatchers.IO) {
            try {
                return@withContext Result.success(db.keyDao().getByName(name))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        return key.map {
            if (it == null) {
                return Result.failure(Exception("No key"))
            }
            return withContext(Dispatchers.Default) {
                // TODO: decrypt passphrase
                loadSSHPrivateKey(it.keyStr, it.passphrase)
            }
        }
    }

    private val _getAllKeysRes = MutableViewModelStateFlow(Result.success(listOf<String>()))
    val getAllKeysRes = _getAllKeysRes.flow

    suspend fun getAllKeys() {
        val db = DB.getInstance(appContext)
        withContext(Dispatchers.IO) {
            try {
                val keys = db.keyDao().getAll().map { it.name!! }
                _getAllKeysRes.update { Result.success(keys) }
            } catch (e: Exception) {
                _getAllKeysRes.update { Result.failure(e) }
            }
        }
    }

    fun clearSshKeyInputs() {
        sshKeyName.update { "" }
        sshKeyStr.update { "" }
        sshKeyPassphrase.update { "" }
    }

    private val _storeSshKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val storeSshKeyRes = _storeSshKeyRes.flow

    suspend fun storeSshKey(): Result<Unit> {
        val name = sshKeyName.value
        val keyStr = sshKeyStr.value
        val passphrase = sshKeyPassphrase.value
        if (name.isEmpty()) {
            val res = Result.failure<Unit>(Exception("Name may not be empty"))
            _storeSshKeyRes.update { res }
            return res
        }
        if (keyStr.isEmpty()) {
            val res = Result.failure<Unit>(Exception("Key may not be empty"))
            _storeSshKeyRes.update { res }
            return res
        }
        val db = DB.getInstance(appContext)
        withContext(Dispatchers.Default) {
            // TODO: encrypt passphrase
        }
        return withContext(Dispatchers.IO) {
            try {
                db.keyDao().insertAll(
                    SshKey(
                        name = name,
                        keyStr = keyStr,
                        passphrase = passphrase,
                    )
                )
                val res = Result.success(Unit)
                _storeSshKeyRes.update { res }
                clearSshKeyInputs()
                return@withContext res
            } catch (e: Exception) {
                val res = Result.failure<Unit>(e)
                _storeSshKeyRes.update { res }
                return@withContext res
            }
        }
    }

    private val _deleteSshKeyRes = MutableViewModelStateFlow(Result.success(Unit))
    val deleteSshKeyRes = _deleteSshKeyRes.flow

    suspend fun deleteSshKey(
        name: String
    ): Result<Unit> {
        val db = DB.getInstance(appContext)
        return withContext(Dispatchers.IO) {
            try {
                // TODO: encrypt passphrase
                db.keyDao().deleteByName(name)
                val res = Result.success(Unit)
                _deleteSshKeyRes.update { res }
                return@withContext res
            } catch (e: Exception) {
                val res = Result.failure<Unit>(Exception(e))
                _deleteSshKeyRes.update { res }
                return@withContext res
            }
        }
    }

    val sshKeyName = MutableViewModelStateFlow("")
    val sshKeyStr = MutableViewModelStateFlow("")
    val sshKeyPassphrase = MutableViewModelStateFlow("")

    val gitRepoName = MutableViewModelStateFlow("")
    val gitRepoUrl = MutableViewModelStateFlow("")

    private val rootDir = appContext.getDir("gittool", Context.MODE_PRIVATE)
    private val sshHomeDir = File(rootDir, "ssh/home")
    private val sshDir = File(sshHomeDir, "ssh")

    private suspend fun getSshSessionFactory(name: String): Result<MemSshdSessionFactory> {
        return loadSshKey(name).map {
            MemSshdSessionFactory(it, sshHomeDir, sshDir)
        }
    }

    private class MemSshdSessionFactory(
        private val key: KeyPair, private val homeDir: File, private val sshDir: File
    ) : SshdSessionFactory() {
        override fun getHomeDirectory(): File {
            return homeDir
        }

        override fun getSshDirectory(): File {
            return sshDir
        }

        override fun getSshConfig(sshDir: File?): File? {
            return null
        }

        override fun getDefaultKeys(sshDir: File?): Iterable<KeyPair> {
            return MemKeyProvider(key)
        }

        override fun getDefaultIdentities(sshDir: File?): List<Path> {
            return listOf()
        }

        // TODO: determine if needed
        override fun getServerKeyDatabase(homeDir: File?, sshDir: File?): ServerKeyDatabase? {
            return super.getServerKeyDatabase(homeDir, sshDir)
        }

        override fun getDefaultKnownHostsFiles(sshDir: File?): List<Path> {
            return listOf()
        }
    }

    private class MemKeyProvider(private val key: KeyPair) : Iterable<KeyPair> {
        override fun iterator(): Iterator<KeyPair> {
            return listOf(key).iterator()
        }
    }

    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(GitViewModel::class.java)) {
                throw IllegalArgumentException("Invalid view model class for factory")
            }
            return GitViewModel(appContext) as T
        }
    }
}

internal fun loadSSHPrivateKey(pkStr: String, passphrase: String): Result<KeyPair> {
    val keypairs = try {
        SecurityUtils.loadKeyPairIdentities(
            null,
            null,
            pkStr.byteInputStream(),
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