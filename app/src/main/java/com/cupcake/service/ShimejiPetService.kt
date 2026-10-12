package com.cupcake.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.cupcake.MainActivity
import com.cupcake.R
import com.cupcake.ai.CharacterManager
import kotlin.math.abs

/**
 * Shimeji Desktop-Pet Floating Overlay Service.
 * Renders a draggable, animated desktop pet mascot on top of Android apps with 3D VRM support.
 */
class ShimejiPetService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var petVrmView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val handler = Handler(Looper.getMainLooper())
    private var speechBubbleDismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
                showSpeechBubble(text)
            }
            else -> {
                val avatar = intent?.getStringExtra(EXTRA_AVATAR)
                val customGif = intent?.getStringExtra(EXTRA_GIF)
                val characterName = intent?.getStringExtra(EXTRA_NAME) ?: "Companion"
                setupFloatingView(avatar, customGif, characterName)
            }
        }
        return START_STICKY
    }

    private fun setupFloatingView(avatar: String?, customGif: String?, characterName: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays: permission missing")
            stopSelf()
            return
        }

        if (floatView != null) {
            // Already created, update image
            updatePetVisual(avatar, customGif)
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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

        val petImageView = view.findViewById<ImageView>(R.id.pet_image)
        val petEmojiView = view.findViewById<TextView>(R.id.pet_emoji)
        val speechBubble = view.findViewById<View>(R.id.speech_bubble)
        val speechText = view.findViewById<TextView>(R.id.speech_text)

        val closeBtn = view.findViewById<View>(R.id.pet_close_btn)
        closeBtn?.setOnClickListener {
            Log.i(TAG, "Dismissing floating pet via close button")
            stopSelf()
        }

        // Initialize 3D VRM Mascot WebView
        petVrmView = view.findViewById<WebView>(R.id.pet_vrm_view)?.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {}
            loadUrl("file:///android_asset/vrm/vrm_viewer.html")
        }

        updatePetVisual(avatar, customGif)

        // VRM expression cycle on tap
        val vrmExpressions = listOf("happy", "flirty", "excited", "pout", "surprised", "relaxed")
        var emotionIndex = 0

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
                                // Trigger VRM walking motion when moving around screen
                                petVrmView?.evaluateJavascript("window.setWalking && window.setWalking(true);", null)
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
                        petVrmView?.evaluateJavascript("window.setWalking && window.setWalking(false);", null)

                        if (isDragging) {
                            val displayMetrics = resources.displayMetrics
                            val screenHeight = displayMetrics.heightPixels
                            // Cut off / dismiss when dragged below screen
                            if (layoutParams!!.y > screenHeight - 160) {
                                Log.i(TAG, "Pet dragged below screen, cutting off / dismissing")
                                stopSelf()
                                return true
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime < 350) {
                                // Double Click -> Open App
                                openApp()
                            } else {
                                // Single Click -> Emotion reaction + Cute Quote
                                val exp = vrmExpressions[emotionIndex % vrmExpressions.size]
                                petVrmView?.evaluateJavascript("window.setExpression && window.setExpression('$exp', 1.0);", null)

                                if (exp == "excited" || exp == "flirty") {
                                    petVrmView?.evaluateJavascript("window.setDancing && window.setDancing(true);", null)
                                    handler.postDelayed({
                                        petVrmView?.evaluateJavascript("window.setDancing && window.setDancing(false);", null)
                                    }, 4000)
                                }

                                emotionIndex++

                                val cuteQuotes = listOf(
                                    "✨ Kyaa! I'm right here on your screen!",
                                    "💖 Don't ignore me! Let's play or chat!",
                                    "🐾 Need me? Double tap to open CupCake!",
                                    "💃 Look at my dance! Let's celebrate!",
                                    "🍰 Win a game against me today!",
                                    "⭐ You're doing great! Keep going!"
                                )
                                showSpeechBubble(cuteQuotes.random())
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
        }
    }

    private fun updatePetVisual(avatar: String?, customGif: String?) {
        val view = floatView ?: return
        val petImageView = view.findViewById<ImageView>(R.id.pet_image) ?: return
        val petEmojiView = view.findViewById<TextView>(R.id.pet_emoji) ?: return

        val isVrm = avatar?.contains("marin") == true || avatar?.endsWith(".vrm") == true
        if (isVrm) {
            petVrmView?.visibility = View.VISIBLE
            petImageView.visibility = View.GONE
            petEmojiView.visibility = View.GONE
            return
        }

        petVrmView?.visibility = View.GONE
        val imageSource = customGif?.takeIf { it.isNotBlank() } ?: avatar?.takeIf { it.isNotBlank() }

        if (imageSource != null && (imageSource.startsWith("http") || imageSource.startsWith("content://") || imageSource.startsWith("file://") || imageSource.endsWith(".gif") || imageSource.endsWith(".png") || imageSource.endsWith(".webp") || imageSource.endsWith(".jpg"))) {
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

    fun showSpeechBubble(text: String) {
        val view = floatView ?: return
        val speechBubble = view.findViewById<View>(R.id.speech_bubble) ?: return
        val speechText = view.findViewById<TextView>(R.id.speech_text) ?: return

        speechBubbleDismissRunnable?.let { handler.removeCallbacks(it) }

        speechText.text = text
        speechBubble.visibility = View.VISIBLE

        // Trigger VRM talking animation
        petVrmView?.evaluateJavascript("window.setTalking && window.setTalking(true);", null)

        speechBubbleDismissRunnable = Runnable {
            speechBubble.visibility = View.GONE
            petVrmView?.evaluateJavascript("window.setTalking && window.setTalking(false);", null)
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
        if (floatView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floatView on destroy", e)
            }
            floatView = null
        }
        petVrmView?.destroy()
        petVrmView = null
        speechBubbleDismissRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShimejiPetService"
        private const val CHANNEL_ID = "cupcake_shimeji_channel"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.cupcake.shimeji.START"
        const val ACTION_STOP = "com.cupcake.shimeji.STOP"
        const val ACTION_SHOW_SPEECH = "com.cupcake.shimeji.SHOW_SPEECH"

        const val EXTRA_AVATAR = "extra_avatar"
        const val EXTRA_GIF = "extra_gif"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_TEXT = "extra_text"

        fun start(context: Context, avatar: String? = null, gif: String? = null, name: String? = null) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                putExtra(EXTRA_AVATAR, avatar)
                putExtra(EXTRA_GIF, gif)
                putExtra(EXTRA_NAME, name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun showSpeech(context: Context, text: String) {
            val intent = Intent(context, ShimejiPetService::class.java).apply {
                action = ACTION_SHOW_SPEECH
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }
    }
}
