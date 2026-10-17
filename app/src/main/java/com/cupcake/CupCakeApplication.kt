package com.cupcake

import android.app.Application
import androidx.room.Room
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EnergyManager
import com.cupcake.data.source.local.AppDatabase
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CupCakeApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Enable WebView inspection
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)

        // Initialize character manager
        CharacterManager.initialize(filesDir)

        // Initialize Energy Manager (loads saved state)
        EnergyManager(this)

        // Initialize Native Llama Engine
        com.cupcake.ai.LlamaEngine.initialize(this)

        // Warm the active on-device model in the background for instant first replies
        com.cupcake.ai.ModelPreloader.preload(this)

        // Pre-create database
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cupcake.db"
        ).build()

        // Start WebSocket server for ESP32
        com.cupcake.network.EspWebSocketServer.attach(this)
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
        // Advertise this phone's address over UDP so the ESP32 can find the server
        com.cupcake.network.PhoneBeacon.start(this)

        // Initialize proactive companion outreach
        com.cupcake.ai.ProactiveEngagementManager.initialize(this)

        // Initialize CupCake's point-costing demands (snacks, attention, rematches...)
        com.cupcake.ai.DemandManager.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .respectCacheHeaders(false)
            .build()
    }
}