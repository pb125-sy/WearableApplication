package com.example.wearableapplication // Ensure this matches your package

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant

@Entity(tableName = "time_window_logs")
data class TimeWindowRecords(
    @PrimaryKey
    val windowStartTime: Instant,
    val screenTimeSec: Long,
    val unlockCount: Int,
    val stepsTaken: Int,
    val totalCalories: Int,
    val avgBpm: Int,
    val selfReportedStress: Int?,
    val currentMood: String?,
    val sleepRating: Int?,
    val tirednessLevel: Int?
)
@Dao
interface TimeWindowDao {

    // 1. Insert a new 15-minute block
    // REPLACE tells SQLite to overwrite the row if a block with the exact same timestamp already exists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TimeWindowRecords)

    // 2. Retrieve all records to build your Machine Learning CSV
    // This orders them chronologically so your time-series data is perfectly aligned
    @Query("SELECT * FROM time_window_logs ORDER BY windowStartTime ASC")
    suspend fun getAllRecords(): List<TimeWindowRecords>

    // 3. (Optional but recommended) Clear the database after a successful export
    @Query("DELETE FROM time_window_logs")
    suspend fun deleteAllRecords()

    // 4. Retrieve N most recent records for retroactive labeling
    @Query("SELECT * FROM time_window_logs ORDER BY windowStartTime DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<TimeWindowRecords>
}