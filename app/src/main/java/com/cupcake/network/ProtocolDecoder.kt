package com.cupcake.network

import android.util.Log
import com.cupcake.protocol.Command
import com.cupcake.protocol.CommandResponse
import com.cupcake.protocol.DecodedMessage
import com.cupcake.protocol.EventData
import com.cupcake.protocol.BatteryData
import com.cupcake.protocol.SensorData
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

/**
 * Kotlin port of ESP32 ProtocolHandler for decoding binary BLE notifications.
 * Binary format (little-endian):
 *
 * Sensor Data (0x01) - 37 bytes:
 *   [type:1][sequence:4][timestamp:4][accelX:4][accelY:4][accelZ:4][gyroX:4][gyroY:4][gyroZ:4][temp:4]
 *
 * Battery (0x02) - 9 bytes:
 *   [type:1][voltage:4][percentage:4]
 *
 * Event (0x03) - variable:
 *   [type:1][event_len:1][event_string][json payload]
 *
 * Ping (0x04) - 5 bytes:
 *   [type:1][timestamp:4]
 *
 * Pong (0x84) - 5 bytes:
 *   [type:1][timestamp:4]
 *
 * Response (0x80) - JSON:
 *   id / status / data / error
 *
 * Command (0x81) - JSON or binary
 */

object ProtocolDecoder {

    private const val TAG = "ProtocolDecoder"
    private val json = Json { ignoreUnknownKeys = true }

    // Message type identifiers (must match ESP32)
    const val MSG_TYPE_SENSOR = 0x01
    const val MSG_TYPE_BATTERY = 0x02
    const val MSG_TYPE_EVENT = 0x03
    const val MSG_TYPE_PING = 0x04
    const val MSG_TYPE_PONG = 0x84
    const val MSG_TYPE_RESPONSE = 0x80
    const val MSG_TYPE_COMMAND = 0x81

    // Expected payload sizes
    const val SENSOR_PAYLOAD_SIZE = 37 // 1 + 4 + 4 + 7*4
    const val BATTERY_PAYLOAD_SIZE = 9  // 1 + 2*4
    const val PING_PONG_PAYLOAD_SIZE = 5 // 1 + 4

    /**
     * Decode a BLE notification payload into a typed result.
     */
    fun decode(data: ByteArray): DecodedMessage? {
        return when (data.size) {
            0 -> null
            1 -> null
            else -> {
                val type = data[0].toInt() and 0xFF
                when (type) {
                    MSG_TYPE_SENSOR -> if (data.size >= SENSOR_PAYLOAD_SIZE) decodeSensor(data) else null
                    MSG_TYPE_BATTERY -> if (data.size >= BATTERY_PAYLOAD_SIZE) decodeBattery(data) else null
                    MSG_TYPE_EVENT -> decodeEvent(data)
                    MSG_TYPE_PING -> decodePing(data)
                    MSG_TYPE_PONG -> decodePong(data)
                    MSG_TYPE_RESPONSE -> decodeResponse(data)
                    MSG_TYPE_COMMAND -> decodeCommand(data)
                    else -> {
                        Log.w(TAG, "Unknown message type: 0x${type.toString(16)}")
                        null
                    }
                }
            }
        }
    }

    private fun decodeSensor(data: ByteArray): SensorData? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get()
        val sequence = buffer.getInt().toLong().and(0xFFFFFFFFL)
        val timestamp = buffer.getInt().toLong().and(0xFFFFFFFFL)
        val accelX = buffer.getFloat()
        val accelY = buffer.getFloat()
        val accelZ = buffer.getFloat()
        val gyroX = buffer.getFloat()
        val gyroY = buffer.getFloat()
        val gyroZ = buffer.getFloat()
        val temperature = buffer.getFloat()
        return SensorData(
            sequence = sequence,
            timestamp = Instant.ofEpochMilli(timestamp),
            accelX = accelX,
            accelY = accelY,
            accelZ = accelZ,
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            temperature = temperature
        )
    }

    private fun decodeBattery(data: ByteArray): BatteryData? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get()
        val voltage = buffer.getFloat()
        val percentage = buffer.getFloat()
        return BatteryData(voltage = voltage, percentage = percentage)
    }

    private fun decodePing(data: ByteArray): EventData? {
        if (data.size < PING_PONG_PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // type
        val timestamp = buffer.getInt().toLong().and(0xFFFFFFFFL)
        return EventData(
            eventName = "ping",
            data = mapOf("timestamp" to timestamp)
        )
    }

    private fun decodePong(data: ByteArray): EventData? {
        if (data.size < PING_PONG_PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // type
        val timestamp = buffer.getInt().toLong().and(0xFFFFFFFFL)
        return EventData(
            eventName = "pong",
            data = mapOf("timestamp" to timestamp)
        )
    }

    private fun decodeEvent(data: ByteArray): EventData? {
        if (data.size < 2) return null
        val eventLen = (data[1].toInt() and 0xFF)
        if (data.size < 2 + eventLen) return null
        val eventName = String(data.copyOfRange(2, 2 + eventLen))
        var jsonData: Map<String, Any>? = null
        if (data.size > 2 + eventLen) {
            val jsonBytes = data.copyOfRange(2 + eventLen, data.size)
            val jsonStr = String(jsonBytes)
            try {
                jsonData = json.decodeFromString<Map<String, Any>>(jsonStr)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse event JSON: $jsonStr", e)
            }
        }
        return EventData(eventName = eventName, data = jsonData)
    }

    private fun decodeResponse(data: ByteArray): CommandResponse? {
        val jsonStr = String(data)
        try {
            return json.decodeFromString<CommandResponse>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON: $jsonStr", e)
            return null
        }
    }

    private fun decodeCommand(data: ByteArray): Command? {
        val jsonStr = String(data)
        try {
            return json.decodeFromString<Command>(jsonStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse command JSON: $jsonStr", e)
            return null
        }
    }

    fun encodeCommand(command: Command): ByteArray {
        return try {
            json.encodeToString(command).toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode command", e)
            ByteArray(0)
        }
    }

    fun encodePing(timestamp: Long = System.currentTimeMillis()): ByteArray {
        val buffer = ByteBuffer.allocate(PING_PONG_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MSG_TYPE_PING.toByte())
        buffer.putInt(timestamp.toInt())
        return buffer.array()
    }

    fun encodePong(timestamp: Long = System.currentTimeMillis()): ByteArray {
        val buffer = ByteBuffer.allocate(PING_PONG_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MSG_TYPE_PONG.toByte())
        buffer.putInt(timestamp.toInt())
        return buffer.array()
    }

    // Pre-defined command builders
    object Commands {
        fun startStreaming(deviceId: String, intervalMs: Int = 100): Command {
            return Command(
                action = "start_streaming",
                params = mapOf("deviceId" to deviceId, "intervalMs" to intervalMs)
            )
        }

        fun stopStreaming(deviceId: String): Command {
            return Command(
                action = "stop_streaming",
                params = mapOf("deviceId" to deviceId)
            )
        }

        fun setSensorInterval(deviceId: String, intervalMs: Int): Command {
            return Command(
                action = "set_interval",
                params = mapOf("deviceId" to deviceId, "intervalMs" to intervalMs)
            )
        }

        fun getInfo(deviceId: String): Command {
            return Command(
                action = "info",
                params = mapOf("deviceId" to deviceId)
            )
        }

        fun speak(text: String): Command {
            return Command(
                action = "speak",
                params = mapOf("text" to text)
            )
        }

        fun faceExpression(expression: String): Command {
            return Command(
                action = "face",
                params = mapOf("expression" to expression)
            )
        }

        fun motorAction(action: String): Command {
            return Command(
                action = "motor",
                params = mapOf("action" to action)
            )
        }

        fun otaStart(): Command {
            return Command(
                type = "ota",
                action = "start"
            )
        }
    }
}