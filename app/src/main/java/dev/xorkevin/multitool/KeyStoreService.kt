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

class KeyStoreService(appContext: Context) {
    val keyDB = Room.databaseBuilder(
        appContext, DB::class.java, "keystore-db"
    ).build()

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

    @Database(entities = [SshKey::class], version = 1)
    abstract class DB : RoomDatabase() {
        abstract fun keyDao(): SshKeyDao
    }
}
