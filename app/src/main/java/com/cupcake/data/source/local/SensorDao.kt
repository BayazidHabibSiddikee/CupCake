package com.cupcake.data.source.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cupcake.data.model.SensorReading
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: SensorReading): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<SensorReading>)

    @Query("SELECT * FROM sensor_readings WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(deviceId: String): SensorReading?

    @Query("SELECT * FROM sensor_readings WHERE deviceId = :deviceId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getRange(deviceId: String, start: Instant, end: Instant): List<SensorReading>

    @Query("SELECT * FROM sensor_readings WHERE deviceId = :deviceId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun observeRange(deviceId: String, start: Instant, end: Instant): Flow<List<SensorReading>>

    @Query("DELETE FROM sensor_readings WHERE timestamp < :olderThan")
    suspend fun deleteOld(olderThan: Instant): Int

    @Query("DELETE FROM sensor_readings WHERE deviceId = :deviceId AND timestamp < :olderThan")
    suspend fun deleteOldForDevice(deviceId: String, olderThan: Instant): Int

    @Query("SELECT COUNT(*) FROM sensor_readings WHERE deviceId = :deviceId")
    suspend fun getCount(deviceId: String): Int

    @Query("SELECT MAX(timestamp) FROM sensor_readings WHERE deviceId = :deviceId")
    suspend fun getLatestTimestamp(deviceId: String): Instant?
}