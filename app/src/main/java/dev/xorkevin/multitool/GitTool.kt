package dev.xorkevin.multitool

import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
    }
}

@Composable
fun GitCloneInput() {
    val gitViewModel: GitViewModel =
        scopedViewModel(
            factory = GitViewModel.Factory(
                LocalContext.current.getDir("gittool", Context.MODE_PRIVATE)
            )
        )
    var name by gitViewModel.name.collectAsStateWithLifecycle()
    var url by gitViewModel.url.collectAsStateWithLifecycle()
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

class GitViewModel(rootDir: File) : ViewModel() {
    val name = MutableViewModelStateFlow("")
    val url = MutableViewModelStateFlow("")

    private val key: KeyPair? = null

    private val sshHomeDir = File(rootDir, "ssh/home")
    private val sshDir = File(sshHomeDir, "ssh")

    private fun getSshSessionFactory(): Result<MemSshdSessionFactory> {
        if (key == null) {
            return Result.failure(Exception("Key not loaded"))
        }
        return Result.success(MemSshdSessionFactory(key, sshHomeDir, sshDir))
    }

    private class MemSshdSessionFactory(
        private val key: KeyPair,
        private val homeDir: File,
        private val sshDir: File
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

        override fun getServerKeyDatabase(homeDir: File?, sshDir: File?): ServerKeyDatabase? {
            TODO("Not implemented")
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

    class Factory(private val rootDir: File) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(GitViewModel::class.java)) {
                throw IllegalArgumentException("Invalid view model class for factory")
            }
            return GitViewModel(rootDir) as T
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