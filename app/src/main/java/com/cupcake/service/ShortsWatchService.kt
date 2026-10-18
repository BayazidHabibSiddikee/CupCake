package com.cupcake.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.cupcake.ai.EmotionAssets
import com.cupcake.game.PointsManager
import com.cupcake.ime.PersonaEngine
import java.util.Calendar

/**
 * CupCake's "doom-scroll radar".
 *
 * Watches YouTube / Facebook / Instagram and counts each Short / Reel the user
 * swipes through. Every short costs coins and winds the companion up: a few is a
 * raised eyebrow, a lot is a full-on tantrum. Detection uses the accessibility
 * tree (Shorts/Reels containers and page titles), so it needs the user to enable
 * the service once in Settings → Accessibility.
 */
class ShortsWatchService : AccessibilityService() {

    private val prefs by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private var inShortsFeed = false
    private var sessionCount = 0
    private var lastCountAt = 0L
    private var lastScanAt = 0L
    private var lastReactLevel = -1
    private var lastReactAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        rollDayIfNeeded()
        Log.i(TAG, "Shorts watchdog connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return

        if (pkg !in TARGETS) {
            inShortsFeed = false
            return
        }

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastScanAt > SCAN_THROTTLE_MS) {
                    lastScanAt = now
                    inShortsFeed = scanForShortsFeed(pkg)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (inShortsFeed) onShortWatched(pkg)
            }
        }
    }

    /** Walks the active window looking for Shorts/Reels UI markers. */
    private fun scanForShortsFeed(pkg: String): Boolean {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return false
        return try {
            containsMarker(root, pkg)
        } catch (e: Exception) {
            Log.w(TAG, "scan failed", e)
            false
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
    }

    private fun containsMarker(node: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (node == null) return false
        val blob = buildString {
            node.viewIdResourceName?.let { append(it.toString().lowercase()).append(' ') }
            node.contentDescription?.let { append(it.toString().lowercase()).append(' ') }
            node.text?.let { append(it.toString().lowercase()) }
        }
        if (blob.isNotBlank() && matchesMarker(pkg, blob)) return true
        for (i in 0 until node.childCount) {
            if (containsMarker(node.getChild(i), pkg)) return true
        }
        return false
    }

    private fun matchesMarker(pkg: String, blob: String): Boolean = when {
        pkg == YOUTUBE ->
            blob.contains("reel") || blob.contains("shorts") || blob.contains("short_player")
        pkg == INSTAGRAM ->
            blob.contains("clips") || blob.contains("reel")
        pkg == FACEBOOK ->
            blob.contains("reel") || blob.contains("shorts")
        else -> false
    }

    private fun onShortWatched(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastCountAt < COUNT_THROTTLE_MS) return
        lastCountAt = now
        sessionCount++
        rollDayIfNeeded()
        val today = prefs.getInt(KEY_TODAY, 0) + 1
        prefs.edit()
            .putInt(KEY_TODAY, today)
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .apply()

        // Dock coins — silently no-op when the wallet is empty.
        PointsManager.spendPersisted(this, COIN_COST_PER_SHORT)

        Log.i(TAG, "Short #$sessionCount today=$today pkg=$pkg")
        react(today)
    }

    private fun react(today: Int) {
        val level = when {
            today >= 30 -> 3
            today >= 15 -> 2
            today >= 5 -> 1
            today >= 1 -> 0
            else -> -1
        }
        if (level < 0) return

        val now = System.currentTimeMillis()
        // Only speak when the mood escalates, or sparingly as it keeps climbing.
        val escalated = level > lastReactLevel
        if (!escalated && now - lastReactAt < REACT_THROTTLE_MS) return

        val (emotion, quip) = when (level) {
            0 -> "annoyed" to "Oi... shorts again? I'm watching you scroll 👀"
            1 -> "annoyed" to "That's $today shorts now. My patience is getting thin hmph 😑"
            2 -> "angry" to "$today?! Put the phone DOWN. You're bleeding coins because of me 😤"
            else -> "angry" to "THAT'S IT — $today shorts. I'm taking your coins and I am NOT happy 🔥"
        }

        lastReactLevel = level
        lastReactAt = now
        Log.i(TAG, "React level=$level today=$today → $emotion")

        PersonaEngine.triggerReaction(emotion, quip)
        if (ShimejiPetService.isRunning) {
            val stem = EmotionAssets.espFaceForStem(emotion)
            ShimejiPetService.showSpeech(applicationContext, quip, EmotionAssets.assetForStem(stem))
        }
    }

    private fun rollDayIfNeeded() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val stamp = prefs.getInt(KEY_DAY, -1)
        if (stamp != today) {
            prefs.edit().putInt(KEY_DAY, today).putInt(KEY_TODAY, 0).apply()
            lastReactLevel = -1
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShortsWatchService"
        private const val PREFS = "cupcake_shorts"
        private const val KEY_DAY = "day_of_year"
        private const val KEY_TODAY = "today_count"
        private const val KEY_TOTAL = "total_count"

        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM = "com.instagram.android"
        const val FACEBOOK = "com.facebook.katana"

        private val TARGETS = setOf(YOUTUBE, INSTAGRAM, FACEBOOK)

        /** Coins docked from the wallet for every short/reel watched. */
        const val COIN_COST_PER_SHORT = 2L

        private const val SCAN_THROTTLE_MS = 1_000L
        private const val COUNT_THROTTLE_MS = 3_000L
        private const val REACT_THROTTLE_MS = 20_000L

        @Volatile
        var isConnected = false
            private set

        /** True when the user has enabled the watchdog in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ShortsWatchService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expected) return true
            }
            return false
        }

        fun todayCount(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TODAY, 0)
    }
}
