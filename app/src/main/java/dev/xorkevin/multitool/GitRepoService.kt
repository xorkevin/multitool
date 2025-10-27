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

class GitRepoService(appContext: Context) {
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
        return withContext(Dispatchers.IO) {
            try {
                gitRepoDB.gitRepoDao().deleteByName(name)
                return@withContext Result.success(Unit)
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
