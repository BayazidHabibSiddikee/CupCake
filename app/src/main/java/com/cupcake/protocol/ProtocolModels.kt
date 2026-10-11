package com.cupcake.protocol

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Protocol data models for ESP32 communication.
 */

@Serializable
sealed interface DecodedMessage

@Serializable
data class SensorData(
    val sequence: Long,
    val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val temperature: Float
) : DecodedMessage {
    val accelMagnitude: Float
        get() = Math.sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble()).toFloat()

    val gyroMagnitude: Float
        get() = Math.sqrt((gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ).toDouble()).toFloat()
}

@Serializable
data class BatteryData(
    val voltage: Float,
    val percentage: Float
) : DecodedMessage

@Serializable
data class EventData(
    val eventName: String,
    val data: Map<String, String>? = null
) : DecodedMessage

@Serializable
data class CommandResponse(
    val id: String,
    val status: String,
    val data: Map<String, String>? = null,
    val error: String? = null
) : DecodedMessage

@Serializable
data class Command(
    val type: String = "control",
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: String,
    val params: Map<String, String> = emptyMap()
) : DecodedMessage

