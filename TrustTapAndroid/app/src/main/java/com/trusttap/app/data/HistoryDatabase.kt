package com.trusttap.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analysis_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: String,
    val thumbnailPath: String?,
    val createdAt: Long,
    val claimedCaption: String?,
    val responseJson: String
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM analysis_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM analysis_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class TrustTapDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: TrustTapDatabase? = null

        fun get(context: android.content.Context): TrustTapDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrustTapDatabase::class.java,
                    "trusttap.db"
                ).build().also { instance = it }
            }
    }
}
