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
import android.net.Uri
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
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.cupcake.MainActivity
import com.cupcake.R
import com.cupcake.ai.CharacterManager
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

    private var baseImageSource: String? = null

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
            else -> {
                val avatar = intent?.getStringExtra(EXTRA_AVATAR)
                val characterName = intent?.getStringExtra(EXTRA_NAME) ?: "Companion"
                setupFloatingView(avatar, characterName)
            }
        }
        return START_STICKY
    }

    private fun setupFloatingView(avatar: String?, characterName: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays: permission missing")
            stopSelf()
            return
        }

        if (floatView != null) {
            // Already created, update image
            updatePetVisual(avatar)
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


        // Inflate view
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_shimeji_pet, null)
        floatView = view

        updatePetVisual(avatar)

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
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime < 350) {
                                // Double Click -> Open App
                                openApp()
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
            view.post { startRoaming() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
        }
    }

    /**
     * Drives the desktop-pet loop: strolls horizontally across the whole screen with a
     * gentle walk-bob, and periodically breaks into a bouncing spin-dance for a few seconds.
     * Pauses automatically while the user drags the pet.
     */
    private fun startRoaming() {
        if (roamRunnable != null) return
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        val params = layoutParams ?: return
        petWidth = floatView?.width?.takeIf { it > 0 } ?: (120 * metrics.density).toInt()
        petHeight = floatView?.height?.takeIf { it > 0 } ?: (140 * metrics.density).toInt()
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

        if (dancing) {
            // Bounce in place + spin wiggle.
            params.y = baseY - (abs(kotlin.math.sin(tick / 4.0)) * 28).toInt()
            val wobble = (kotlin.math.sin(tick / 3.0) * 16f).toFloat()
            applyPetTransforms(view, faceRight = facing >= 0, rotation = wobble)
        } else {
            // Stroll left/right across the whole screen.
            val speed = ((screenW / 260).coerceIn(3, 12))
            params.x += facing * speed
            val maxX = (screenW - petWidth).coerceAtLeast(0)
            if (params.x <= 0) {
                params.x = 0
                facing = 1
            } else if (params.x >= maxX) {
                params.x = maxX
                facing = -1
            }
            params.y = baseY + (kotlin.math.sin(tick / 7.0) * 7).toInt()
            applyPetTransforms(view, faceRight = facing >= 0, rotation = 0f)

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

    private fun applyPetTransforms(view: View, faceRight: Boolean, rotation: Float) {
        val scaleX = if (faceRight) 1f else -1f
        view.findViewById<ImageView>(R.id.pet_image)?.let {
            it.scaleX = scaleX
            it.rotation = rotation
        }
        view.findViewById<TextView>(R.id.pet_emoji)?.let {
            it.scaleX = scaleX
            it.rotation = rotation
        }
    }

    private fun stopRoaming() {
        roamRunnable?.let { handler.removeCallbacks(it) }
        roamRunnable = null
    }

    private fun updatePetVisual(avatar: String?) {
        val view = floatView ?: return
        val petImageView = view.findViewById<ImageView>(R.id.pet_image) ?: return
        val petEmojiView = view.findViewById<TextView>(R.id.pet_emoji) ?: return

        // Base look = the 2D sprite from the emotion set (no GIFs anymore).
        val imageSource = avatar?.takeIf { it.isNotBlank() }
        baseImageSource = imageSource

        if (imageSource != null && (imageSource.startsWith("http") || imageSource.startsWith("content://") || imageSource.startsWith("file://") || imageSource.endsWith(".png") || imageSource.endsWith(".webp") || imageSource.endsWith(".jpg"))) {
            petEmojiView.visibility = View.GONE
            petImageView.visibility = View.VISIBLE
            petImageView.load(imageSource) {
                crossfade(true)
                placeholder(R.drawable.ic_pet_placeholder)
                error(R.drawable.ic_pet_placeholder)
            }
        } else {
            // Emoji or fallback
            petImageView.visibility = View.GONE
            petEmojiView.visibility = View.VISIBLE
            petEmojiView.text = avatar?.takeIf { it.isNotBlank() } ?: "🐱"
        }
    }

    /**
     * Swaps the 2D pet face to [asset] for a few seconds, then restores the base look.
     * Reflects the companion's live emotion without opening a speech bubble.
     */
    private fun setEmotionFace(asset: String) {
        val view = floatView ?: return
        if (baseImageSource == null) return
        val petImageView = view.findViewById<ImageView>(R.id.pet_image) ?: return

        faceRestoreRunnable?.let { handler.removeCallbacks(it) }
        petImageView.visibility = View.VISIBLE
        petImageView.load(asset) {
            crossfade(true)
            placeholder(R.drawable.ic_pet_placeholder)
            error(R.drawable.ic_pet_placeholder)
        }

        faceRestoreRunnable = Runnable {
            val restore = baseImageSource
            if (restore != null) {
                petImageView.load(restore) { crossfade(true) }
            }
        }
        handler.postDelayed(faceRestoreRunnable!!, 4000)
    }

    fun showSpeechBubble(text: String, emotionAsset: String? = null) {
        val view = floatView ?: return
        val speechBubble = view.findViewById<View>(R.id.speech_bubble) ?: return
        val speechText = view.findViewById<TextView>(R.id.speech_text) ?: return

        speechBubbleDismissRunnable?.let { handler.removeCallbacks(it) }

        speechText.text = text
        speechBubble.visibility = View.VISIBLE

        // Swap the 2D pet face to the spoken emotion, then restore the base look.
        val petImageView = view.findViewById<ImageView>(R.id.pet_image)
        if (emotionAsset != null && petImageView != null) {
            petImageView.visibility = View.VISIBLE
            petImageView.load(emotionAsset) {
                crossfade(true)
                placeholder(R.drawable.ic_pet_placeholder)
                error(R.drawable.ic_pet_placeholder)
            }
        }

        speechBubbleDismissRunnable = Runnable {
            speechBubble.visibility = View.GONE
            if (emotionAsset != null && petImageView != null) {
                val restore = baseImageSource
                if (restore != null) {
                    petImageView.load(restore) { crossfade(true) }
                }
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

        const val EXTRA_AVATAR = "extra_avatar"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_EMOTION = "extra_emotion"
        const val EXTRA_FACE_ASSET = "extra_face_asset"
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
