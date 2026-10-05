package com.cupcake.jni

import android.util.Log

object NativeLoader {

    private const val TAG = "NativeLoader"

    private val loadedLibraries = mutableSetOf<String>()

    fun loadLibrary(name: String) {
        synchronized(loadedLibraries) {
            if (name in loadedLibraries) {
                Log.d(TAG, "Library already loaded: $name")
                return
            }

            try {
                System.loadLibrary(name)
                loadedLibraries.add(name)
                Log.i(TAG, "Successfully loaded library: $name")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load library: $name", e)
                throw e
            }
        }
    }

    fun loadLibraries(vararg names: String) {
        names.forEach { loadLibrary(it) }
    }

    fun isLoaded(name: String): Boolean = name in loadedLibraries

    fun getLoadedLibraries(): Set<String> = loadedLibraries.toSet()
}