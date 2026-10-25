package com.cupcake.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.cupcake.service.ActivityMonitorService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ActivityContextProvider bridges ActivityMonitorService with the AI engine.
 * It listens for activity changes and provides the current context to ChatViewModel.
 */
@Singleton
class ActivityContextProvider @Inject constructor(@ApplicationContext private val context: Context) {

    private val TAG = "ActivityContextProvider"

    private val _currentActivity = MutableStateFlow<ActivityClassification?>(null)
    val currentActivity: StateFlow<ActivityClassification?> = _currentActivity.asStateFlow()

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != ActivityMonitorService.ACTION_ACTIVITY_CHANGED) return

            val packageName = intent.getStringExtra(ActivityMonitorService.EXTRA_PACKAGE_NAME) ?: ""
            val appLabel = intent.getStringExtra(ActivityMonitorService.EXTRA_APP_LABEL)
            val activityTypeName = intent.getStringExtra(ActivityMonitorService.EXTRA_ACTIVITY_TYPE) ?: "UNKNOWN"
            val confidence = intent.getFloatExtra(ActivityMonitorService.EXTRA_CONFIDENCE, 0.1f)
            val timestamp = intent.getLongExtra(ActivityMonitorService.EXTRA_TIMESTAMP, System.currentTimeMillis())

            val activityType = ActivityType.valueOf(activityTypeName)

            val classification = ActivityClassification(
                packageName = packageName,
                appLabel = appLabel,
                activityType = activityType,
                confidence = confidence,
                timestamp = timestamp
            )

            _currentActivity.value = classification
            Log.i(TAG, "Activity updated: ${activityType} (${appLabel ?: packageName})")
        }
    }

    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered) return
        val filter = IntentFilter(ActivityMonitorService.ACTION_ACTIVITY_CHANGED)
        context.registerReceiver(receiver, filter)
        isRegistered = true
        Log.i(TAG, "Activity monitoring started")
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        isRegistered = false
        _currentActivity.value = null
        Log.i(TAG, "Activity monitoring stopped")
    }

    /**
     * Get an activity-aware system prompt augmentation for the AI engine.
     * This is prepended to the system prompt so the AI knows what the user is doing.
     */
    fun getActivityAwarePromptAugmentation(): String {
        val activity = _currentActivity.value ?: return ""

        val description = ActivityClassifier.getDescription(activity.activityType)
        val emotion = ActivityClassifier.getEmotionForActivity(activity.activityType)
        val greetings = ActivityClassifier.getGreetingForActivity(activity.activityType)
        val greeting = greetings.random()

        return """
        [ACTIVITY CONTEXT]
        The user is currently: $description
        App: ${activity.appLabel ?: activity.packageName}
        Emotion tag: [$emotion]
        The user may be distracted or multitasking. Be brief, playful, and respectful of their attention.
        You may gently mention what they're doing if it feels natural.
        [/ACTIVITY CONTEXT]
        """.trimIndent()
    }

    /**
     * Get a greeting line based on current activity.
     */
    fun getActivityGreeting(): String? {
        val activity = _currentActivity.value ?: return null
        val greetings = ActivityClassifier.getGreetingForActivity(activity.activityType)
        return greetings.random()
    }

    /**
     * Check if activity monitoring is active.
     */
    fun isMonitoring(): Boolean = isRegistered
}
