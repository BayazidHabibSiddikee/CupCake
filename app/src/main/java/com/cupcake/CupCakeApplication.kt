package com.cupcake

import android.app.Application
import androidx.room.Room
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.ai.LlamaEngine
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.native.QwenNative
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CupCakeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize native libraries
        QwenNative.initialize(this)
        LlamaEngine.initialize(this)

        // Initialize character manager
        CharacterManager.initialize(filesDir)

        // Initialize Energy Manager (loads saved state)
        EnergyManager(this)

        // Pre-create database
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cupcake.db"
        ).build()

        // Start WebSocket server for ESP32
        com.cupcake.network.EspWebSocketServer.start(
            port = 8080,
            onMessage = { msg ->
                // Handle incoming ESP32 messages
                android.util.Log.d("EspServer", "Received: ${msg.type} - ${msg.payload}")
            },
            onConnectionChange = { sessionId, connected ->
                android.util.Log.i("EspServer", "ESP32 $sessionId ${if (connected) "connected" else "disconnected"}")
            }
        )
    }
}