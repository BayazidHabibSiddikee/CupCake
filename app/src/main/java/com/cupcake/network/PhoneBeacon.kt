package com.cupcake.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Broadcasts the phone's WebSocket server address over UDP so the ESP32 companion
 * can discover it after its own WiFi provisioning. The board listens on the same
 * port and connects to [EspWebSocketServer]'s `/esp` route.
 *
 * Payload: `{"type":"phone","port":8080}` — the ESP derives the IP from the
 * datagram's source address.
 */
object PhoneBeacon {

    private const val TAG = "PhoneBeacon"
    private const val BEACON_PORT = 44880
    private const val INTERVAL_MS = 2_000L

    private var socket: DatagramSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context, serverPort: Int = 8080) {
        if (socket != null) return
        scope.launch {
            try {
                val sock = DatagramSocket().apply {
                    broadcast = true
                    reuseAddress = true
                }
                socket = sock
                val payload = JSONObject()
                    .put("type", "phone")
                    .put("port", serverPort)
                    .toString().toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                Log.i(TAG, "Beaconing phone address on UDP $BEACON_PORT every ${INTERVAL_MS}ms")
                while (isActive) {
                    try {
                        sock.send(DatagramPacket(payload, payload.size, broadcast, BEACON_PORT))
                    } catch (e: Exception) {
                        Log.w(TAG, "Beacon send failed: ${e.message}")
                    }
                    delay(INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beacon stopped", e)
                socket = null
            }
        }
    }

    fun stop() {
        socket?.close()
        socket = null
    }
}
