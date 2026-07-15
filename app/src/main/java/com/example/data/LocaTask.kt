package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "loca_tasks")
data class LocaTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float, // in meters
    val isActive: Boolean = true,
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayCoordinates: String
        get() = String.format("%.5f, %.5f", latitude, longitude)
}

@Dao
interface LocaTaskDao {
    @Query("SELECT * FROM loca_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<LocaTask>>

    @Query("SELECT * FROM loca_tasks WHERE isActive = 1")
    suspend fun getActiveTasks(): List<LocaTask>

    @Query("SELECT * FROM loca_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): LocaTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: LocaTask): Long

    @Update
    suspend fun updateTask(task: LocaTask)

    @Query("DELETE FROM loca_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("UPDATE loca_tasks SET isTriggered = :triggered WHERE id = :id")
    suspend fun updateTriggeredStatus(id: Int, triggered: Boolean)

    @Query("UPDATE loca_tasks SET isActive = :active, isTriggered = 0 WHERE id = :id")
    suspend fun updateActiveStatus(id: Int, active: Boolean)
}

@Database(entities = [LocaTask::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locaTaskDao(): LocaTaskDao
}
