package dev.xorkevin.multitool

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.sshd.common.util.io.PathUtils.setUserHomeFolderResolver
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.PublicKey
import java.util.function.Supplier

class GitRepoService(appContext: Context, private val keyStore: KeyStoreService) {
    private val rootDir = appContext.getDir("git_repo_service", Context.MODE_PRIVATE)
    private val reposDir = File(rootDir, "repos")
    private val sshHomeDir = File(rootDir, "ssh/home")
    private val sshDir = File(sshHomeDir, "ssh")

    init {
        setUserHomeFolderResolver(DirResolver(sshHomeDir))
    }

    private class DirResolver(private val dir: File) : Supplier<Path> {
        override fun get(): Path {
            return dir.toPath()
        }
    }

    private suspend fun getSshSessionFactory(name: String): Result<MemSshdSessionFactory> {
        return keyStore.getSshKey(name).map {
            MemSshdSessionFactory(it, sshHomeDir, sshDir)
        }
    }

    private class MemSshdSessionFactory(
        key: KeyPair, private val homeDir: File, private val sshDir: File
    ) : SshdSessionFactory(), TransportConfigCallback {
        private val memKeyProvider = MemKeyProvider(key)

        override fun getDefaultPreferredAuthentications(): String {
            return "publickey"
        }

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
            return memKeyProvider
        }

        override fun getDefaultIdentities(sshDir: File?): List<Path> {
            return listOf()
        }

        override fun getServerKeyDatabase(homeDir: File?, sshDir: File?): ServerKeyDatabase? {
            return EmptyServerKeyDB
        }

        override fun getDefaultKnownHostsFiles(sshDir: File?): List<Path> {
            return listOf()
        }

        override fun configure(transport: Transport?) {
            if (transport !is SshTransport) {
                return
            }
            transport.sshSessionFactory = this
        }
    }

    private class MemKeyProvider(private val key: KeyPair) : Iterable<KeyPair> {
        override fun iterator(): Iterator<KeyPair> {
            return listOf(key).iterator()
        }
    }

    private object EmptyServerKeyDB : ServerKeyDatabase {
        override fun lookup(
            connectAddress: String?,
            remoteAddress: InetSocketAddress?,
            config: ServerKeyDatabase.Configuration?
        ): List<PublicKey> {
            return listOf()
        }

        override fun accept(
            connectAddress: String?,
            remoteAddress: InetSocketAddress?,
            serverKey: PublicKey?,
            config: ServerKeyDatabase.Configuration?,
            provider: CredentialsProvider?
        ): Boolean {
            return true
        }
    }

    suspend fun getRepo(name: String): Result<GitRepo> {
        val repo = withContext(Dispatchers.IO) {
            try {
                return@withContext Result.success(gitRepoDB.gitRepoDao().getByName(name))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }.getOrElse { return Result.failure(it) }
        if (repo == null) {
            return Result.failure(Exception("No repo"))
        }
        return Result.success(repo)
    }

    suspend fun getAllRepos(): Result<List<GitRepo>> {
        return withContext(Dispatchers.IO) {
            try {
                return@withContext Result.success(gitRepoDB.gitRepoDao().getAll())
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun addRepos(vararg repo: GitRepo): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                gitRepoDB.gitRepoDao().insertAll(*repo)
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun rmRepo(name: String): Result<Unit> {
        rmRepoDir(name).getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            try {
                gitRepoDB.gitRepoDao().deleteByName(name)
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun rmRepoDir(name: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val gitRepoDir = File(reposDir, name)
                if (!gitRepoDir.exists()) {
                    return@withContext Result.success(Unit)
                }
                gitRepoDir.delete()
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun cloneRepo(name: String): Result<Unit> {
        val repo = getRepo(name).getOrElse { return Result.failure(it) }
        val sshSessionFactory =
            getSshSessionFactory(repo.sshKeyName).getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            try {
                val gitRepoDir = File(reposDir, name)
                if (gitRepoDir.exists()) {
                    return@withContext Result.success(Unit)
                }
                Git.cloneRepository().setURI(repo.url).setBranch(repo.branch)
                    .setDirectory(gitRepoDir).setTransportConfigCallback(sshSessionFactory).call()
                    .use { }
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    data class RepoFile(val path: String, val isDir: Boolean)

    private val ignoredFileNames = setOf(".git", ".gitattributes", ".gpg-id", ".gpg-id.sig")

    suspend fun listRepoContents(name: String, dir: String): Result<List<RepoFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val gitRepoDir = File(reposDir, name)
                val subdir = if (dir == "") {
                    gitRepoDir
                } else {
                    File(gitRepoDir, dir)
                }
                if (!subdir.exists() or !subdir.isDirectory) {
                    return@withContext Result.failure(Exception("Repo not cloned"))
                }
                return@withContext Result.success(subdir.listFiles()?.flatMap {
                    if (ignoredFileNames.contains(it.name)) {
                        listOf()
                    } else {
                        listOf(RepoFile(Paths.get(dir, it.name).toString(), it.isDirectory))
                    }
                }?.toList() ?: listOf())
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }

    private val gitRepoDB = Room.databaseBuilder(
        appContext, DB::class.java, "git-repo-db"
    ).build()

    @Database(entities = [GitRepo::class], version = 1)
    abstract class DB : RoomDatabase() {
        abstract fun gitRepoDao(): GitRepoDao
    }

    @Entity(tableName = "git_repos", primaryKeys = ["name"])
    data class GitRepo(
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "url") val url: String,
        @ColumnInfo(name = "branch") val branch: String,
        @ColumnInfo(name = "ssh_key_name") val sshKeyName: String,
    )

    @Dao
    interface GitRepoDao {
        @Query("SELECT * FROM git_repos WHERE name = :name")
        suspend fun getByName(name: String): GitRepo?

        @Query("SELECT * FROM git_repos ORDER BY name")
        suspend fun getAll(): List<GitRepo>

        @Insert
        suspend fun insertAll(vararg rootKeys: GitRepo)

        @Query("DELETE FROM git_repos WHERE name = :name")
        suspend fun deleteByName(name: String): Int
    }
}
