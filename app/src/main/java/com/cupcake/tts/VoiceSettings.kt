package com.cupcake.tts

import android.content.Context
import android.content.SharedPreferences

/** Persistent "always speak" preference for the companion's voice. */
object VoiceSettings {

    private const val PREFS = "cupcake_voice"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true) // voice always on by default

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
