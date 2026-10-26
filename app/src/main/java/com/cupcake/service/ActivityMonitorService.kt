package com.cupcake.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cupcake.ai.ActivityClassifier
import com.cupcake.ai.ActivityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CupCake's "eye": watches what apps the user is currently using and
 * classifies their activity (gaming, YouTube, social media, chatting, etc.)
 *
 * Long-running work lives in a foreground service so the companion can stay
 * contextually aware while the user is in other apps.
 */
class ActivityMonitorService : Service() {

    private val TAG = "ActivityMonitorService"

    private var scope = CoroutineScope(Dispatchers.Default + Job())
    private var lastClassification: com.cupcake.ai.ActivityClassification? = null
    @Volatile private var monitoring = false

    private val activityManager: ActivityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private val pm: PackageManager by lazy {
        this@ActivityMonitorService.packageManager
    }

    private val shortsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != "com.cupcake.action.SHORTS_DETECTED") return

            val packageName = intent.getStringExtra("package_name") ?: "com.google.android.youtube"
            val todayCount = intent.getIntExtra("today_count", 0)
            val reactLevel = intent.getIntExtra("react_level", 0)

            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                "YouTube"
            }

            val classification = com.cupcake.ai.ActivityClassifier.classify(packageName, appLabel)

            lastClassification = classification
            Log.i(TAG, "Shorts detected: $packageName (today=$todayCount level=$reactLevel)")
            broadcastActivityChange(classification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Register Shorts detection broadcast receiver.
        // Internal app broadcast only, so explicitly mark it not exported on
        // API 34+ where a receiver flag is mandatory.
        val filter = IntentFilter("com.cupcake.action.SHORTS_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shortsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(shortsReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CupCake is watching")
            .setContentText("Tracking your activities")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)

        scope.launch {
            while (monitoring) {
                try {
                    val classification = classifyCurrentActivity()
                    if (classification != null && classification != lastClassification) {
                        lastClassification = classification
                        Log.i(TAG, "Activity changed: ${classification.activityType} (${classification.appLabel ?: classification.packageName})")
                        broadcastActivityChange(classification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error classifying activity: ${e.message}")
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopMonitoring() {
        monitoring = false
        stopForeground(true)
        stopSelf()
    }

    private fun classifyCurrentActivity(): com.cupcake.ai.ActivityClassification? {
        return try {
            val runningApps = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Android 10+: getRunningAppProcesses gives limited info
                    activityManager.runningAppProcesses
                }
                else -> activityManager.runningAppProcesses
            }

            val topProcess = runningApps.firstOrNull { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
            if (topProcess != null && topProcess.pkgList.isNotEmpty()) {
                val packageName = topProcess.pkgList[0]
                val appLabel = try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
                ActivityClassifier.classify(packageName, appLabel)
            } else {
                ActivityClassifier.classify("", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to classify current activity: ${e.message}")
            null
        }
    }

    private fun broadcastActivityChange(classification: com.cupcake.ai.ActivityClassification) {
        val intent = Intent(ACTION_ACTIVITY_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, classification.packageName)
            putExtra(EXTRA_APP_LABEL, classification.appLabel)
            putExtra(EXTRA_ACTIVITY_TYPE, classification.activityType.name)
            putExtra(EXTRA_CONFIDENCE, classification.confidence)
            putExtra(EXTRA_DESCRIPTION, ActivityClassifier.getDescription(classification.activityType))
            putExtra(EXTRA_TIMESTAMP, classification.timestamp)
        }
        sendBroadcast(intent)
    }

    /**
     * Get the last known activity classification.
     */
    fun getLastClassification(): com.cupcake.ai.ActivityClassification? = lastClassification

    /**
     * Check if the app is running.
     */
    fun isMonitoring(): Boolean = monitoring

    override fun onDestroy() {
        try {
            unregisterReceiver(shortsReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ActivityMonitorService"
        private const val CHANNEL_ID = "activity_monitor"
        private const val NOTIF_ID = 43
        private const val CHECK_INTERVAL_MS = 5_000L

        const val ACTION_START = "com.cupcake.action.ACTIVITY_MONITOR_START"
        const val ACTION_STOP = "com.cupcake.action.ACTIVITY_MONITOR_STOP"
        const val ACTION_ACTIVITY_CHANGED = "com.cupcake.action.ACTIVITY_CHANGED"

        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_TIMESTAMP = "extra_timestamp"

        @Volatile var isMonitoring = false
            private set

        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ActivityMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            val intent = android.content.Intent(context, ActivityMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Activity Monitor",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
