package com.cupcake.network

import android.util.Log
import com.cupcake.protocol.Command
import com.cupcake.protocol.CommandResponse
import com.cupcake.protocol.DecodedMessage
import com.cupcake.protocol.EventData
import com.cupcake.protocol.BatteryData
import com.cupcake.protocol.SensorData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin port of ESP32 ProtocolHandler for decoding binary BLE notifications.
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
                // The ESP32 speaks JSON for command envelopes/responses
                // (e.g. {"type":"resp",...}); detect that before the binary types.
                if (type == '{'.code) {
                    return decodeJson(data)
                }
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
            timestamp = timestamp,
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
            data = mapOf("timestamp" to timestamp.toString())
        )
    }

    private fun decodePong(data: ByteArray): EventData? {
        if (data.size < PING_PONG_PAYLOAD_SIZE) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // type
        val timestamp = buffer.getInt().toLong().and(0xFFFFFFFFL)
        return EventData(
            eventName = "pong",
            data = mapOf("timestamp" to timestamp.toString())
        )
    }

    /**
     * Decodes the ESP32's JSON envelope. Command responses look like
     * `{"type":"resp","id":..,"status":..,"data":{..}}`; `data` values are
     * flattened to strings so numeric/bool fields survive the Map<String,String>
     * response model (WiFi scan results arrive as a JSON string field).
     */
    private fun decodeJson(data: ByteArray): DecodedMessage? {
        val text = String(data, Charsets.UTF_8)
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON message: $text", e)
            return null
        }

        return when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "resp" -> {
                val id = root["id"]?.jsonPrimitive?.contentOrNull ?: return null
                val status = root["status"]?.jsonPrimitive?.contentOrNull ?: "ok"
                val dataObj = root["data"] as? JsonObject
                val map = dataObj?.mapValues { (_, value) ->
                    (value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                        ?: value.toString()
                }
                CommandResponse(id = id, status = status, data = map)
            }
            "event" -> {
                val name = root["event"]?.jsonPrimitive?.contentOrNull ?: "event"
                EventData(eventName = name, data = null)
            }
            else -> null
        }
    }

    private fun decodeEvent(data: ByteArray): EventData? {
        if (data.size < 2) return null
        val eventLen = (data[1].toInt() and 0xFF)
        if (data.size < 2 + eventLen) return null
        val eventName = String(data.copyOfRange(2, 2 + eventLen))
        var jsonData: Map<String, String>? = null
        if (data.size > 2 + eventLen) {
            val jsonBytes = data.copyOfRange(2 + eventLen, data.size)
            val jsonStr = String(jsonBytes)
            try {
                jsonData = json.decodeFromString<Map<String, String>>(jsonStr)
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
                params = mapOf("deviceId" to deviceId, "intervalMs" to intervalMs.toString())
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
                params = mapOf("deviceId" to deviceId, "intervalMs" to intervalMs.toString())
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

        // --- WiFi provisioning (BLE) -------------------------------------
        fun wifiScan(): Command = Command(type = "wifi", action = "scan")

        fun wifiConnect(ssid: String, password: String): Command = Command(
            type = "wifi",
            action = "connect",
            params = mapOf("ssid" to ssid, "password" to password)
        )

        fun wifiStatus(): Command = Command(type = "wifi", action = "status")

        fun wifiForget(): Command = Command(type = "wifi", action = "forget")
    }
}
