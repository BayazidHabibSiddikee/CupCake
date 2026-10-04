package com.cupcake.network

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress

object EspWebSocketServer {

    private const val TAG = "EspWebSocketServer"
    private const val DEFAULT_PORT = 8080
    private val json = Json { ignoreUnknownKeys = true }

    private var server: ApplicationEngine? = null
    private var serverScope: CoroutineScope? = null
    private val sessions = mutableMapOf<String, WebSocketSession>()
    private val messageChannel = Channel<IncomingMessage>(100)
    private var onMessageReceived: ((IncomingMessage) -> Unit)? = null
    private var onConnectionChange: ((String, Boolean) -> Unit)? = null

    data class IncomingMessage(
        val sessionId: String,
        val type: String,
        val payload: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class OutgoingMessage(
        val type: String,
        val payload: Any,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJson(): String = json.encodeToString(this)
    }

    // Message types
    object MessageType {
        const val AUDIO = "audio"
        const val FACE = "face"
        const val MOTOR = "motor"
        const val SENSOR = "sensor"
        const val STATUS = "status"
        const val COMMAND = "command"
        const val PING = "ping"
        const val PONG = "pong"
        const val CONFIG = "config"
    }

    // Face expressions
    object FaceExpression {
        const val NEUTRAL = "neutral"
        const val HAPPY = "happy"
        const val SAD = "sad"
        const val ANGRY = "angry"
        const val RAGE = "rage"
        const val EXCITED = "excited"
        const val THINKING = "thinking"
        const val SLEEPY = "sleepy"
        const val WINK = "wink"
        const val HEART_EYES = "heart_eyes"
    }

    // Motor actions
    object MotorAction {
        const val IDLE = "idle"
        const val NOD = "nod"
        const val SHAKE = "shake"
        const val TANTUM = "tantrum"
        const val DANCE = "dance"
        const val WAVE = "wave"
        const val LOOK_LEFT = "look_left"
        const val LOOK_RIGHT = "look_right"
    }

    fun start(
        port: Int = DEFAULT_PORT,
        onMessage: ((IncomingMessage) -> Unit)? = null,
        onConnectionChange: ((String, Boolean) -> Unit)? = null
    ): Boolean {
        this.onMessageReceived = onMessage
        this.onConnectionChange = onConnectionChange

        return try {
            serverScope = CoroutineScope(Dispatchers.IO)
            serverScope?.launch {
                server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                    install(WebSockets)
                    routing {
                        webSocket("/esp") {
                            handleWebSocketSession()
                        }
                        webSocket("/esp/ws") {
                            handleWebSocketSession()
                        }
                    }
                }.start(wait = false)
                Log.i(TAG, "WebSocket server started on port $port")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            false
        }
    }

    private suspend fun handleWebSocketSession() {
        val sessionId = "esp_${System.currentTimeMillis()}"
        val session = WebSocketSession(sessionId, this@webSocket)
        sessions[sessionId] = session
        
        onConnectionChange?.invoke(sessionId, true)
        Log.i(TAG, "ESP32 connected: $sessionId")

        try {
            incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val msg = json.decodeFromString<IncomingMessage>(frame.readText())
                            msg.copy(sessionId = sessionId).also { onMessageReceived?.invoke(it) }
                            messageChannel.trySend(msg.copy(sessionId = sessionId))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse message: ${frame.readText()}")
                        }
                    }
                    is Frame.Binary -> {
                        // Handle binary audio data
                        val data = frame.readBytes()
                        onMessageReceived?.invoke(IncomingMessage(
                            sessionId = sessionId,
                            type = MessageType.AUDIO,
                            payload = data.toHexString()
                        ))
                    }
                    is Frame.Close -> {
                        close(CloseReason(CloseReason.Codes.NORMAL, "Client closed"))
                    }
                    is Frame.Pong -> {
                        // Heartbeat
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket error for $sessionId", e)
        } finally {
            sessions.remove(sessionId)
            onConnectionChange?.invoke(sessionId, false)
            Log.i(TAG, "ESP32 disconnected: $sessionId")
        }
    }

    fun sendToEsp(sessionId: String, type: String, payload: Any): Boolean {
        val session = sessions[sessionId] ?: return false
        val msg = OutgoingMessage(type, payload).toJson()
        return try {
            session.webSocket.send(Frame.Text(msg))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to $sessionId", e)
            false
        }
    }

    fun broadcast(type: String, payload: Any): Int {
        val msg = OutgoingMessage(type, payload).toJson()
        var sent = 0
        sessions.forEach { (id, session) ->
            try {
                session.webSocket.send(Frame.Text(msg))
                sent++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast to $id")
            }
        }
        return sent
    }

    fun sendFaceExpression(expression: String, duration: Int = 2000) {
        broadcast(MessageType.FACE, mapOf(
            "expression" to expression,
            "duration" to duration
        ))
    }

    fun sendMotorAction(action: String, params: Map<String, Any> = emptyMap()) {
        broadcast(MessageType.MOTOR, mapOf(
            "action" to action,
            "params" to params
        ))
    }

    fun sendAudio(audioData: ByteArray) {
        sessions.forEach { (_, session) ->
            try {
                session.webSocket.send(Frame.Binary(io.ktor.utils.io.ByteBuffer.wrap(audioData)))
            } catch (e: Exception) {
                Log.w(TAG, "Audio send failed")
            }
        }
    }

    fun sendCommand(command: String, params: Map<String, Any> = emptyMap()) {
        broadcast(MessageType.COMMAND, mapOf(
            "cmd" to command,
            "params" to params
        ))
    }

    fun getConnectedDevices(): List<String> = sessions.keys.toList()

    fun isConnected(sessionId: String): Boolean = sessions.containsKey(sessionId)

    fun stop() {
        sessions.values.forEach { it.webSocket.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server stopping")) }
        sessions.clear()
        server?.stop()
        serverScope?.cancel()
        Log.i(TAG, "WebSocket server stopped")
    }

    private data class WebSocketSession(
        val id: String,
        val webSocket: DefaultWebSocketSession
    )
}

private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02X".format(it) }
}