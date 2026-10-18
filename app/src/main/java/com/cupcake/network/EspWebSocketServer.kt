package com.cupcake.network

import android.content.Context
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.InetSocketAddress

object EspWebSocketServer {

    private const val TAG = "EspWebSocketServer"
    private const val DEFAULT_PORT = 8080
    private val json = Json { ignoreUnknownKeys = true }

    private var server: ApplicationEngine? = null
    private var serverScope: CoroutineScope? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = mutableMapOf<String, WebSocketSession>()
    private val messageChannel = Channel<IncomingMessage>(100)
    private var onMessageReceived: ((IncomingMessage) -> Unit)? = null
    private var onConnectionChange: ((String, Boolean) -> Unit)? = null

    /** Set once at startup so the server can serve bundled emotion art to the ESP board. */
    @Volatile
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun setOnConnectionChangeListener(listener: ((String, Boolean) -> Unit)?) {
        onConnectionChange = listener
    }

    fun setOnMessageReceivedListener(listener: ((IncomingMessage) -> Unit)?) {
        onMessageReceived = listener
    }

    data class IncomingMessage(
        val sessionId: String = "",
        val type: String = "",
        val payload: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    data class OutgoingMessage(
        val type: String,
        val payload: Map<String, Any?> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toJson(): String = JSONObject(
            mapOf(
                "type" to type,
                "payload" to JSONObject(payload).toString(),
                "timestamp" to timestamp
            )
        ).toString()
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
        const val SURPRISED = "surprised"
        const val CONFUSED = "confused"
        const val ANNOYED = "annoyed"
    }

    // Motor actions
    object MotorAction {
        const val IDLE = "idle"
        const val NOD = "nod"
        const val SHAKE = "shake"
        const val TANTRUM = "tantrum"
        // Legacy misspelling kept for callers compiled against it (P2-4).
        const val TANTUM = TANTRUM
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
                            if (!verifyPairingToken()) return@webSocket
                            handleWebSocketSession()
                        }
                        webSocket("/esp/ws") {
                            if (!verifyPairingToken()) return@webSocket
                            handleWebSocketSession()
                        }
                        // Serve the companion's emotion art so the ESP board can render
                        // the exact same image the phone is showing.
                        get("/assets/cupcake/emotions/{file}") {
                            val file = call.parameters["file"].orEmpty()
                            if (!file.matches(Regex("[a-zA-Z0-9_]{1,32}\\.png"))) {
                                call.respond(HttpStatusCode.BadRequest)
                                return@get
                            }
                            val bytes = try {
                                appContext?.assets?.open("cupcake/emotions/$file")?.use { it.readBytes() }
                            } catch (e: Exception) {
                                null
                            }
                            if (bytes == null) {
                                call.respond(HttpStatusCode.NotFound)
                            } else {
                                call.respondBytes(bytes, ContentType.Image.PNG)
                            }
                        }
                        get("/assets/cupcake/emotions") {
                            val list = com.cupcake.ai.EmotionAssets.ALL_STEMS.map { stem ->
                                mapOf(
                                    "emotion" to stem,
                                    "face" to com.cupcake.ai.EmotionAssets.espFaceForStem(stem),
                                    "image" to com.cupcake.ai.EmotionAssets.httpPathForStem(stem)
                                )
                            }
                            call.respondText(
                                org.json.JSONArray(list).toString(),
                                ContentType.Application.Json
                            )
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

    /**
     * Rejects WS handshakes that don't carry a valid pairing token in the query
     * string. Open (no tokens issued yet: nothing is paired) is allowed so the
     * first-time provisioning still works.
     */
    private suspend fun DefaultWebSocketServerSession.verifyPairingToken(): Boolean {
        val owned = com.cupcake.ble.DeviceAuthStore.ownedTokens()
        if (owned.isEmpty()) return true
        val token = call.request.queryParameters["token"]
        if (token != null && token in owned) return true
        Log.w(TAG, "ESP32 WS rejected: invalid pairing token")
        close(io.ktor.websocket.CloseReason(
            io.ktor.websocket.CloseReason.Codes.PROTOCOL_ERROR, "auth_required"))
        return false
    }

    private suspend fun DefaultWebSocketSession.handleWebSocketSession() {
        val sessionId = "esp_${System.currentTimeMillis()}"
        val session = WebSocketSession(sessionId, this)
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
                    else -> {
                        // Ping/Pong heartbeats ignored
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

    fun sendToEsp(
        sessionId: String,
        type: String,
        payload: Map<String, Any?> = emptyMap()
    ): Boolean {
        val session = sessions[sessionId] ?: return false
        val msg = OutgoingMessage(type, payload).toJson()
        ioScope.launch {
            try {
                session.webSocket.send(Frame.Text(msg))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to $sessionId", e)
            }
        }
        return true
    }

    fun broadcast(type: String, payload: Map<String, Any?> = emptyMap()): Int {
        val msg = OutgoingMessage(type, payload).toJson()
        var sent = 0
        sessions.forEach { (id, session) ->
            ioScope.launch {
                try {
                    session.webSocket.send(Frame.Text(msg))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to broadcast to $id")
                }
            }
            sent++
        }
        return sent
    }

    fun sendFaceExpression(
        expression: String,
        duration: Int = 2000,
        emotion: String? = null,
        imagePath: String? = null
    ) {
        broadcast(MessageType.FACE, buildFacePayload(expression, duration, emotion, imagePath))
    }

    fun sendMotorAction(action: String, params: Map<String, Any> = emptyMap()) {
        broadcast(MessageType.MOTOR, mapOf(
            "action" to action,
            "params" to params
        ))
    }

    fun sendAudio(audioData: ByteArray) {
        sessions.forEach { (_, session) ->
            ioScope.launch {
                try {
                    session.webSocket.send(Frame.Binary(true, audioData))
                } catch (e: Exception) {
                    Log.w(TAG, "Audio send failed")
                }
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
        val active = sessions.values.toList()
        sessions.clear()
        ioScope.launch {
            active.forEach {
                try {
                    it.webSocket.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server stopping"))
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing session", e)
                }
            }
        }
        server?.stop(1000, 2000)
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