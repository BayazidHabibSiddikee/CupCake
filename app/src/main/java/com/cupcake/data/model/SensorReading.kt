package com.cupcake.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sensor_readings",
    indices = [
        androidx.room.Index(value = ["deviceId", "timestamp"]),
        androidx.room.Index(value = ["sequence"])
    ]
)
data class SensorReading(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val timestamp: Instant,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val temperature: Float,
    val batteryVoltage: Float,
    val sequence: Long
)