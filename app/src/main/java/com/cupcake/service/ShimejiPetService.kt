package com.cupcake.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.cupcake.MainActivity
import com.cupcake.R
import com.cupcake.ai.CharacterManager
import com.cupcake.ai.EmotionAssets
import com.cupcake.ai.MoodStore
import kotlin.math.abs

/**
 * Shimeji Desktop-Pet Floating Overlay Service.
 * Renders a draggable, animated desktop pet mascot on top of Android apps.
 */
class ShimejiPetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val handler = Handler(Looper.getMainLooper())
    private var speechBubbleDismissRunnable: Runnable? = null
    private var faceRestoreRunnable: Runnable? = null

    private var petRig: com.cupcake.pet.PetRigView? = null
    private var baseEmotion = com.cupcake.ai.EmotionAssets.canonicalStem("normal")

    // Lively roaming/dancing animation state.
    private var roamRunnable: Runnable? = null
    private var screenW = 0
    private var screenH = 0
    private var petWidth = 0
    private var petHeight = 0
    private var baseY = 0
    private var facing = 1
    private var tick = 0
    private var danceTicksLeft = 0
    private var danceCooldown = 90

    /** Silent-treatment state: frozen in place, angry face, no chatter. */
    private var angryMode = false
    private var angerCoolRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ensureNotificationChannel()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_SPEECH -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: "Hello!"
                val emotionAsset = intent.getStringExtra(EXTRA_EMOTION)
                showSpeechBubble(text, emotionAsset)
            }
            ACTION_SET_FACE -> {
                intent.getStringExtra(EXTRA_FACE_ASSET)?.let { setEmotionFace(it) }
            }
            ACTION_SET_ANGER -> {
                val level = intent.getIntExtra(EXTRA_ANGER, MoodStore.LEVEL_CALM)
                val line = intent.getStringExtra(EXTRA_ANGRY_LINE)
                val emotion = intent.getStringExtra(EXTRA_EMOTION)
                applyAnger(level, line, emotion)
            }
            else -> {
                val characterName = intent?.getStringExtra(EXTRA_NAME) ?: "Companion"
                val avatar = intent?.getStringExtra(EXTRA_AVATAR)
                setupFloatingView(characterName, avatar)
            }
        }
        return START_STICKY
    }

    private fun setupFloatingView(characterName: String, avatar: String? = null) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays: permission missing")
            stopSelf()
            return
        }

        if (floatView != null) {
            // Already created, nothing else to refresh — the rig self-animates.
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 350
        }


        // Inflate view — the articulated rig draws the robot (no static sprite).
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_shimeji_pet, null)
        floatView = view
        petRig = view.findViewById(R.id.pet_rig)
        // Start from the companion's chosen art (e.g. "happy") if we know it.
        EmotionAssets.stemFromAsset(avatar)?.let { stem ->
            baseEmotion = stem
            petRig?.emotion = stem
        }

        // Drag & Click Logic
        view.setOnTouchListener(object : View.OnTouchListener {
            private var lastClickTime: Long = 0

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null || layoutParams == null) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (abs(dx) > 10 || abs(dy) > 10) {
                            if (!isDragging) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            petRig?.gait = com.cupcake.pet.Gait.IDLE
                            layoutParams!!.x = initialX + dx
                            layoutParams!!.y = initialY + dy
                            windowManager?.updateViewLayout(floatView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            val displayMetrics = resources.displayMetrics
                            val screenHeight = displayMetrics.heightPixels
                            // Fluid bubble dismissal: drag down to bottom of screen
                            if (layoutParams!!.y > screenHeight - 200) {
                                Log.i(TAG, "Pet bubbled down to bottom, dismissing")
                                stopSelf()
                                return true
                            }
                            // Resume strolling from wherever the pet was dropped.
                            baseY = layoutParams!!.y.coerceIn(
                                0,
                                (screenHeight - petHeight).coerceAtLeast(0)
                            )
                            if (!angryMode) startRoaming()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime < 350) {
                                // Double Click -> Open App
                                openApp()
                            } else if (angryMode) {
                                // She is sulking: a gentle tap is an apology and she thaws.
                                forgiveCompanion()
                            } else {
                                // Single Click -> Cute Quote + celebrate with a dance
                                val cuteQuotes = listOf(
                                    "✨ Kyaa! I'm right here on your screen!",
                                    "💖 Don't ignore me! Let's play or chat!",
                                    "🐾 Need me? Double tap to open CupCake!",
                                    "💃 Look at my dance! Let's celebrate!",
                                    "🍰 Win a game against me today!",
                                    "⭐ You're doing great! Keep going!"
                                )
                                showSpeechBubble(cuteQuotes.random())
                                danceTicksLeft = 40
                                danceCooldown = 140
                            }
                            lastClickTime = now
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatView, layoutParams)
            Log.i(TAG, "Shimeji pet overlay attached successfully")
            // Resuming mid-sulk? Stay frozen and glaring instead of strolling happily.
            view.post {
                if (MoodStore.isMaxAngry(this)) {
                    applyAnger(MoodStore.LEVEL_MAX, null, null)
                } else {
                    startRoaming()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
        }
    }

    /**
     * Drives the desktop-pet loop: strolls horizontally across the whole screen and
     * periodically breaks into a dance. The rig animates limbs/face itself; this loop
     * only moves the window and flips gait.
     */
    private fun startRoaming() {
        if (roamRunnable != null) return
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        val params = layoutParams ?: return
        petWidth = floatView?.width?.takeIf { it > 0 } ?: (132 * metrics.density).toInt()
        petHeight = floatView?.height?.takeIf { it > 0 } ?: (148 * metrics.density).toInt()
        baseY = params.y.coerceIn(0, (screenH - petHeight).coerceAtLeast(0))

        val runner = object : Runnable {
            override fun run() {
                stepPet()
                handler.postDelayed(this, FRAME_MS)
            }
        }
        roamRunnable = runner
        handler.postDelayed(runner, FRAME_MS)
    }

    private fun stepPet() {
        val params = layoutParams ?: return
        val view = floatView ?: return
        if (isDragging) return

        tick++
        val dancing = danceTicksLeft > 0
        if (dancing) {
            danceTicksLeft--
        } else if (danceCooldown > 0) {
            danceCooldown--
        }
        val rig = petRig ?: return
        if (dancing) {
            rig.facingLeft = facing < 0
            rig.gait = com.cupcake.pet.Gait.DANCE
            params.y = baseY
        } else {
            // Stroll left/right across the whole screen.
            val speed = ((screenW / 200).coerceIn(4, 14))
            params.x += facing * speed
            val maxX = (screenW - petWidth).coerceAtLeast(0)
            if (params.x <= 0) {
                params.x = 0
                facing = 1
            } else if (params.x >= maxX) {
                params.x = maxX
                facing = -1
            }
            params.y = baseY
            rig.facingLeft = facing < 0
            rig.gait = com.cupcake.pet.Gait.WALK

            if (danceCooldown <= 0 && (0..6).random() == 0) {
                danceTicksLeft = 30 + (0..24).random()
                danceCooldown = 140 + (0..160).random()
            }
        }

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "roam updateViewLayout failed", e)
        }
    }

    private fun stopRoaming() {
        roamRunnable?.let { handler.removeCallbacks(it) }
        roamRunnable = null
    }

    /**
     * Applies the companion's anger level to the overlay. At [MoodStore.LEVEL_MAX] she
     * enters silent treatment: stops roaming, glares with the angry face and refuses to
     * chatter (she may deliver a single [line] as she snaps, then goes quiet). Any lower
     * level restores the lively roaming pet.
     */
    private fun applyAnger(level: Int, line: String?, emotionAsset: String?) {
        if (level >= MoodStore.LEVEL_MAX) {
            val wasAngry = angryMode
            angryMode = true
            stopRoaming()
            petRig?.gait = com.cupcake.pet.Gait.IDLE
            // Glare without touching baseEmotion, so the thaw restores the real face.
            faceRestoreRunnable?.let { handler.removeCallbacks(it) }
            petRig?.emotion = "angry"
            scheduleAngerCoolOff()
            // Deliver a single cross line as she goes cold, but only on the transition so
            // she does not keep re-announcing it every cycle.
            if (!wasAngry && !line.isNullOrBlank()) {
                showSpeechBubble(line, EmotionAssets.assetForStem("angry"), force = true)
            }
        } else {
            angerCoolRunnable?.let { handler.removeCallbacks(it) }
            angerCoolRunnable = null
            if (angryMode) {
                angryMode = false
                restoreBaseFace()
                startRoaming()
            }
            if (!line.isNullOrBlank()) {
                showSpeechBubble(line, emotionAsset ?: EmotionAssets.assetForStem("annoyed"))
            }
        }
    }

    /**
     * Nothing else will poke the pet when the anger cooldown simply lapses, so
     * poll once per cooldown and thaw her back to the lively roaming pet.
     */
    private fun scheduleAngerCoolOff() {
        angerCoolRunnable?.let { handler.removeCallbacks(it) }
        val check = object : Runnable {
            override fun run() {
                if (!MoodStore.isMaxAngry(this@ShimejiPetService)) {
                    applyAnger(MoodStore.LEVEL_CALM, null, null)
                } else {
                    handler.postDelayed(this, MoodStore.COOLDOWN_MS + 2_000L)
                }
            }
        }
        angerCoolRunnable = check
        handler.postDelayed(check, MoodStore.COOLDOWN_MS + 2_000L)
    }

    /**
     * The user tapped the sulk to make it up to her: forgiveness clears the mood,
     * lifts the frozen glare and brings back the lively roaming pet.
     */
    private fun forgiveCompanion() {
        Log.i(TAG, "User apologised — lifting silent treatment")
        MoodStore.calmDown(this)
        angerCoolRunnable?.let { handler.removeCallbacks(it) }
        angerCoolRunnable = null
        angryMode = false
        danceTicksLeft = 0
        restoreBaseFace()
        startRoaming()
        showSpeechBubble("Aww… okay, friends again 💕", null, force = true)
    }

    /** Persistently shows [stem] as the pet face (no auto-restore). */
    private fun setPetFace(stem: String) {
        faceRestoreRunnable?.let { handler.removeCallbacks(it) }
        baseEmotion = stem
        petRig?.emotion = stem
    }

    /** Restores the pet's default face after a temporary/angry face. */
    private fun restoreBaseFace() {
        val rig = petRig ?: return
        rig.emotion = baseEmotion
    }

    /**
     * Adopts [stemOrAsset] as the pet's persistent face. Pushed by the chat/game
     * whenever the companion's mood changes, so the floating overlay stays in sync
     * with the in-app emotion indicator instead of flashing and reverting.
     */
    private fun setEmotionFace(stemOrAsset: String) {
        val rig = petRig ?: return
        // While sulking she keeps the angry face instead of mirroring chat emotions.
        if (angryMode) return
        val stem = EmotionAssets.stemFromAsset(stemOrAsset) ?: EmotionAssets.canonicalStem(stemOrAsset)
        faceRestoreRunnable?.let { handler.removeCallbacks(it) }
        faceRestoreRunnable = null
        baseEmotion = stem
        rig.emotion = stem
    }

    fun showSpeechBubble(text: String, emotionAsset: String? = null, force: Boolean = false) {
        // Silent treatment: ignore cheerful chatter, but still allow the single angry
        // line she snaps out when the anger peaks.
        if (angryMode && !force) return
        val view = floatView ?: return
        val speechBubble = view.findViewById<View>(R.id.speech_bubble) ?: return
        val speechText = view.findViewById<TextView>(R.id.speech_text) ?: return

        speechBubbleDismissRunnable?.let { handler.removeCallbacks(it) }

        speechText.text = text
        speechBubble.visibility = View.VISIBLE
        petRig?.speaking = true

        // Swap the pet face to the spoken emotion while she talks, then restore.
        val emotionStem = emotionAsset?.let {
            EmotionAssets.stemFromAsset(it) ?: EmotionAssets.canonicalStem(it)
        }
        if (emotionStem != null) {
            petRig?.emotion = emotionStem
        }

        speechBubbleDismissRunnable = Runnable {
            speechBubble.visibility = View.GONE
            petRig?.speaking = false
            // Only restore when no newer emotion was pushed while this bubble was up.
            val rig = petRig
            if (emotionStem != null && rig != null && rig.emotion == emotionStem) {
                restoreBaseFace()
            }
        }
        handler.postDelayed(speechBubbleDismissRunnable!!, 4000)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CupCake Companion")
            .setContentText("Your floating Shimeji companion is active ✨")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating desktop companion mascot active"
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRoaming()
        if (floatView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floatView on destroy", e)
            }
            floatView = null
        }
        speechBubbleDismissRunnable?.let { handler.removeCallbacks(it) }
        faceRestoreRunnable?.let { handler.removeCallbacks(it) }
        angerCoolRunnable?.let { handler.removeCallbacks(it) }
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShimejiPetService"
        private const val CHANNEL_ID = "cupcake_shimeji_channel"
        private const val NOTIFICATION_ID = 2002
        private const val FRAME_MS = 40L

        /** True while the floating overlay service is actually alive (not just requested). */
        @Volatile
        var isRunning = false

        const val ACTION_START = "com.cupcake.shimeji.START"
        const val ACTION_STOP = "com.cupcake.shimeji.STOP"
        const val ACTION_SHOW_SPEECH = "com.cupcake.shimeji.SHOW_SPEECH"
        const val ACTION_SET_FACE = "com.cupcake.shimeji.SET_FACE"
        const val ACTION_SET_ANGER = "com.cupcake.shimeji.SET_ANGER"

        const val EXTRA_AVATAR = "extra_avatar"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_EMOTION = "extra_emotion"
        const val EXTRA_FACE_ASSET = "extra_face_asset"
        const val EXTRA_ANGER = "extra_anger_level"
        const val EXTRA_ANGRY_LINE = "extra_angry_line"
        const val KEY_RUNNING = "overlay_running"

        fun start(context: Context, avatar: String? = null, name: String? = null) {
            prefs(context).edit().putBoolean(KEY_RUNNING, true).apply()
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                putExtra(EXTRA_AVATAR, avatar)
                putExtra(EXTRA_NAME, name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            prefs(context).edit().putBoolean(KEY_RUNNING, false).apply()
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Restarts the overlay pet if it was enabled last session, so the companion
         * keeps its foreground-service process slot (and the cached model with it).
         */
        fun restoreIfRunning(context: Context) {
            if (prefs(context).getBoolean(KEY_RUNNING, false)) {
                start(context)
            }
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences("cupcake_companion", Context.MODE_PRIVATE)

        fun showSpeech(context: Context, text: String, emotionAsset: String? = null) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_SHOW_SPEECH
                putExtra(EXTRA_TEXT, text)
                if (emotionAsset != null) putExtra(EXTRA_EMOTION, emotionAsset)
            }
            context.startService(intent)
        }

        /**
         * Applies an anger level to the floating pet. At [MoodStore.LEVEL_MAX] she enters
         * silent treatment (frozen, glaring, silent aside from one [line]); lower levels
         * bring back the lively roaming pet.
         */
        fun setAnger(context: Context, level: Int, line: String? = null, emotionAsset: String? = null) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_SET_ANGER
                putExtra(EXTRA_ANGER, level)
                if (line != null) putExtra(EXTRA_ANGRY_LINE, line)
                if (emotionAsset != null) putExtra(EXTRA_EMOTION, emotionAsset)
            }
            context.startService(intent)
        }

        /** Briefly swaps the floating pet's face to [emotionAsset] with no speech bubble. */
        fun setFace(context: Context, emotionAsset: String) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_SET_FACE
                putExtra(EXTRA_FACE_ASSET, emotionAsset)
            }
            context.startService(intent)
        }
    }
}
