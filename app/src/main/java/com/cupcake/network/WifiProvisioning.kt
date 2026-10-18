package com.cupcake.network

import com.cupcake.ble.BleGattClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A WiFi access point reported by the ESP32 during a BLE provisioning scan. */
data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val secure: Boolean
)

/** Current WiFi state reported by the ESP32. */
data class WifiStatus(
    val state: String,
    val ssid: String,
    val ip: String,
    val rssi: Int
) {
    val isConnected: Boolean get() = state == "connected"
    val isConnecting: Boolean get() = state == "connecting"
    val hasFailed: Boolean get() = state == "no_ssid" || state == "failed"
}

/**
 * Drives WiFi provisioning on the ESP32 over the existing BLE (Nordic UART)
 * link, mirroring how other ESP companion apps configure a device: connect over
 * Bluetooth, list nearby networks, send the chosen SSID + password, then poll
 * for the result.
 */
class WifiProvisioner(private val client: BleGattClient) {

    /**
     * Scans for nearby networks on the device. The firmware kicks off an async
     * scan and answers "scanning" until results are cached; we poll within the
     * budget so the BLE link never stalls behind a blocking scan.
     */
    suspend fun scanNetworks(budgetMs: Long = 15_000L): Result<List<WifiNetwork>> {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            val response = client.sendCommand(ProtocolDecoder.Commands.wifiScan(), timeoutMs = 8_000L)
                ?: return Result.failure(IllegalStateException("No response from device"))
            if (response.status != "ok") {
                // "scanning": device accepted the request, results pending — poll again.
                if (response.data?.get("message") == "scanning") {
                    kotlinx.coroutines.delay(1_500)
                    continue
                }
                return Result.failure(IllegalStateException(response.data?.get("message") ?: "Scan failed"))
            }
            val raw = response.data?.get("networks")
                ?: return Result.success(emptyList())
            return Result.success(parseScan(raw))
        }
        return Result.failure(IllegalStateException("Scan timed out"))
    }

    /** Sends credentials; the device associates asynchronously. */
    suspend fun connect(ssid: String, password: String): Result<WifiStatus> {
        val response = client.sendCommand(ProtocolDecoder.Commands.wifiConnect(ssid, password))
            ?: return Result.failure(IllegalStateException("No response from device"))
        if (response.status != "ok") {
            return Result.failure(IllegalStateException(response.data?.get("message") ?: "WiFi connect failed"))
        }
        return Result.success(statusOf(response.data))
    }

    /** Reads the device's current WiFi status. */
    suspend fun status(): WifiStatus? =
        client.sendCommand(ProtocolDecoder.Commands.wifiStatus())?.let { statusOf(it.data) }

    /** Erases stored credentials on the device. */
    suspend fun forget(): Boolean =
        client.sendCommand(ProtocolDecoder.Commands.wifiForget())?.status == "ok"

    private fun statusOf(data: Map<String, String>?): WifiStatus = WifiStatus(
        state = data?.get("state") ?: "unknown",
        ssid = data?.get("ssid") ?: "",
        ip = data?.get("ip") ?: "",
        rssi = data?.get("rssi")?.toIntOrNull() ?: 0
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses the `networks` field: `[{"ssid","rssi","secure"}]`. */
        fun parseScan(raw: String): List<WifiNetwork> {
            return try {
                json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val ssid = obj["ssid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    if (ssid.isBlank()) return@mapNotNull null
                    WifiNetwork(
                        ssid = ssid,
                        rssi = obj["rssi"]?.jsonPrimitive?.intOrNull ?: 0,
                        secure = obj["secure"]?.jsonPrimitive?.booleanOrNull ?: true
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
