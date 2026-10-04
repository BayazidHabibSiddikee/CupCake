package com.cupcake

import android.app.Application
import androidx.room.Room
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.native.QwenNative
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CupCakeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize native library
        QwenNative.initialize(this)

        // Pre-create database
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cupcake.db"
        ).build()
    }
}