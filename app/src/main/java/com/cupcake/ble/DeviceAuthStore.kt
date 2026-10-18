package com.cupcake.ble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.util.UUID

/**
 * Device-binding store. Each CupCake unit is owned by exactly one pairing
 * token — generated on the phone, written to the board over BLE ("auth"/pair), and
 * mirrored here so every later command carries it.
 *
 * Roadmap: once Google accounts land, the token becomes a hash over the
 * account id + device id and [bind] also records the account — the on-device
 * side stays opaque ("single account only" is already what this enforces).
 */
object DeviceAuthStore {

    private const val TAG = "DeviceAuthStore"

    private inline fun prefs(c: Context? = null): SharedPreferences =
        when {
            c != null -> c.getSharedPreferences("device_auth", Context.MODE_PRIVATE)
            else -> appPrefs ?: error("DeviceAuthStore not initialized")
        }
    @Volatile private var appPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (appPrefs == null) {
            synchronized(this) {
                if (appPrefs == null) {
                    appPrefs = context.applicationContext.getSharedPreferences(
                        "device_auth", Context.MODE_PRIVATE)
                }
            }
        }
    }

    /** Mint a fresh pairing token for a board (64 hex chars). */
    fun mintToken(address: String): String {
        val material = "$address:${UUID.randomUUID()}:${System.currentTimeMillis()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    data class Binding(val token: String, val deviceId: String)

    fun binding(address: String): Binding? {
        val token = prefs().getString("token_$address", null) ?: return null
        val id = prefs().getString("device_id_$address", "") ?: ""
        return Binding(token, id)
    }

    fun token(address: String): String? = binding(address)?.token

    fun bind(address: String, token: String, deviceId: String) {
        prefs().edit()
            .putString("token_$address", token)
            .putString("device_id_$address", deviceId)
            .putLong("bound_at_$address", System.currentTimeMillis())
            .apply()
        Log.i(TAG, "paired $address (device $deviceId)")
    }

    fun unbind(address: String) {
        prefs().edit()
            .remove("token_$address")
            .remove("device_id_$address")
            .remove("bound_at_$address")
            .apply()
    }

    /** All pairing tokens this phone legitimately owns (any device). */
    fun ownedTokens(): Set<String> =
        prefs().all.entries.mapNotNull { (k, v) -> if (k.startsWith("token_") && v is String) v else null }.toSet()
}
