package dev.xorkevin.multitool

import android.app.Application
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.nio.file.Path
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
    val gitViewModel: GitViewModel = scopedViewModel()
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

class GitViewModel(private val keyStore: KeyStoreService, rootDir: File) : ViewModel() {
    val gitRepoName = MutableViewModelStateFlow("")
    val gitRepoUrl = MutableViewModelStateFlow("")


    private val sshHomeDir = File(rootDir, "ssh/home")
    private val sshDir = File(sshHomeDir, "ssh")

    private suspend fun getSshSessionFactory(name: String): Result<MemSshdSessionFactory> {
        return keyStore.getSshKey(name).map {
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

    companion object : ScopedViewModelFactory<GitViewModel> {
        override fun create(app: Application): GitViewModel {
            app as MainApplication
            val keyStore = app.container.keyStore
            val rootDir = app.getDir("ssh_key_manager", Context.MODE_PRIVATE)
            return GitViewModel(keyStore, rootDir)
        }
    }
}
