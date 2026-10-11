package com.cupcake.protocol

import java.time.Instant

/**
 * Protocol data models for ESP32 communication.
 * These are kept in a separate file to avoid kapt duplicate class issues.
 * Made internal to prevent duplicate class generation by kapt.
 */

internal sealed interface DecodedMessage

internal data class SensorData(
    val sequence: Long,
    val timestamp: Instant,
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

internal data class BatteryData(
    val voltage: Float,
    val percentage: Float
) : DecodedMessage

internal data class EventData(
    val eventName: String,
    val data: Map<String, Any>?
) : DecodedMessage

internal data class CommandResponse(
    val id: String,
    val status: String,
    val data: Map<String, Any>? = null,
    val error: String? = null
) : DecodedMessage

internal data class Command(
    val type: String = "control",
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: String,
    val params: Map<String, Any> = emptyMap()
)