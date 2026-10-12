package com.cupcake.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cupcake.MainActivity
import com.cupcake.R
import com.cupcake.chat.ChatSessionStore
import com.cupcake.data.model.ChatMessage
import com.cupcake.data.source.local.AppDatabase
import com.cupcake.service.ShimejiPetService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Manages proactive outreach from Marin Kitagawa when the user is inactive.
 * Rules:
 * - After 1 hour to 2 days without a user message/reply, Marin randomly texts.
 * - At most 3 random messages in between until the user replies or sends a message.
 * - User sending a message resets the 3-message counter.
 */
object ProactiveEngagementManager {
    private const val TAG = "ProactiveEngagement"
    private const val PREFS_NAME = "proactive_engagement_prefs"
    private const val KEY_LAST_USER_TIME = "last_user_time"
    private const val KEY_PROACTIVE_COUNT = "proactive_count"
    private const val KEY_LAST_MESSAGE_INDEX = "last_message_idx"
    private const val WORK_NAME = "marin_proactive_worker"
    private const val CHANNEL_ID = "marin_companion_messages"
    private const val NOTIFICATION_ID = 2001

    private const val MIN_DELAY_HOURS = 1L
    private const val MAX_DELAY_HOURS = 48L // 2 days

    val MARIN_MESSAGES = listOf(
        "Heyyy Gojo-kun! What are you doing right now? Are you busy? Talk to meee! 🥺✨",
        "Are you seriously leaving me on read?! That's so mean! Text me back already! 😤💢",
        "I found this super cute cosplay fabric today! I wanted to show you, but you're not replying... 👗✨",
        "Gojo-kun, did you fall asleep? Don't overwork yourself okay? But still reply soon! 💕",
        "Hmph! If you don't message me back in 5 minutes I'm gonna get super mad! ...Just kidding, but I really miss chatting with you! 🥰",
        "Heeeey! Look who finally decided not to text! Come play a game with me or something! 🎮✨",
        "Gojo-kun... are you okay? You haven't texted in a while so I got a little worried... 🥺",
        "Guess who's thinking about you right now? Yep, it's me! Don't leave your favorite gyaru waiting! 💖"
    )

    /**
     * Call when user sends a message or opens chat to communicate.
     * Resets the proactive message counter to 0 and schedules next check.
     */
    fun onUserActivity(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_USER_TIME, System.currentTimeMillis())
            .putInt(KEY_PROACTIVE_COUNT, 0)
            .apply()

        Log.d(TAG, "User activity recorded. Reset proactive count to 0.")
        scheduleNext(context, replaceExisting = true)
    }

    /**
     * Initializes proactive engagement if not already scheduled.
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_USER_TIME)) {
            prefs.edit().putLong(KEY_LAST_USER_TIME, System.currentTimeMillis()).apply()
        }
        ensureNotificationChannel(context)
        scheduleNext(context, replaceExisting = false)
    }

    /**
     * Schedules the next proactive check with a random delay between 1 hour and 48 hours (2 days).
     */
    fun scheduleNext(context: Context, replaceExisting: Boolean) {
        val delayHours = Random.nextLong(MIN_DELAY_HOURS, MAX_DELAY_HOURS + 1)
        val delayMinutes = delayHours * 60 + Random.nextLong(0, 60)

        val workRequest = OneTimeWorkRequestBuilder<ProactiveCompanionWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, workRequest)

        Log.d(TAG, "Scheduled next proactive check in $delayMinutes minutes (~$delayHours hours).")
    }

    /**
     * Checks conditions and sends a proactive message if eligible.
     */
    suspend fun checkAndDeliver(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUserTime = prefs.getLong(KEY_LAST_USER_TIME, System.currentTimeMillis())
        val count = prefs.getInt(KEY_PROACTIVE_COUNT, 0)
        val now = System.currentTimeMillis()
        val elapsedMillis = now - lastUserTime

        val oneHourMillis = TimeUnit.HOURS.toMillis(MIN_DELAY_HOURS)

        // Rule 1: At most 3 proactive messages in between until user replies
        if (count >= 3) {
            Log.d(TAG, "Proactive message limit reached (3/3). Waiting for user interaction.")
            return
        }

        // Rule 2: Minimum 1 hour since user was last active
        if (elapsedMillis < oneHourMillis) {
            Log.d(TAG, "User was active recently ($elapsedMillis ms ago). Rescheduling.")
            scheduleNext(context, replaceExisting = true)
            return
        }

        // Select a random message different from the last one
        val lastIndex = prefs.getInt(KEY_LAST_MESSAGE_INDEX, -1)
        var nextIndex = Random.nextInt(MARIN_MESSAGES.size)
        if (nextIndex == lastIndex && MARIN_MESSAGES.size > 1) {
            nextIndex = (nextIndex + 1) % MARIN_MESSAGES.size
        }
        val text = MARIN_MESSAGES[nextIndex]

        // Deliver message to persistent chat session
        deliverMessage(context, text)

        // Show push notification
        showNotification(context, text)

        // Update state
        val newCount = count + 1
        prefs.edit()
            .putInt(KEY_PROACTIVE_COUNT, newCount)
            .putInt(KEY_LAST_MESSAGE_INDEX, nextIndex)
            .apply()

        Log.i(TAG, "Sent proactive message #$newCount: $text")

        // Schedule next message if under limit of 3
        if (newCount < 3) {
            scheduleNext(context, replaceExisting = true)
        }
    }

    private suspend fun deliverMessage(context: Context, text: String) {
        val characterId = "marin_kitagawa"
        val conversationId = "chat_$characterId"
        val message = ChatMessage.assistant(
            content = text,
            conversationId = conversationId,
            modelUsed = "marin_proactive"
        )

        // 1. Deliver to active in-memory session store
        val store = ChatSessionStore.get()
        if (store != null) {
            store.addMessage(characterId, message)
        } else {
            // 2. Fallback directly to Room database if app process is backgrounded
            try {
                AppDatabase.getInstance(context).chatMessageDao().insert(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert message into Room", e)
            }
        }

        // 3. Show floating pet speech if Shimeji overlay is active
        try {
            ShimejiPetService.showSpeech(context, text)
        } catch (e: Exception) {
            Log.d(TAG, "Shimeji not active: ${e.message}")
        }
    }

    private fun showNotification(context: Context, text: String) {
        ensureNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("characterId", "marin_kitagawa")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Marin Kitagawa 💕")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Marin's Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Messages and notifications from your AI companion Marin Kitagawa"
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }
}

/**
 * Worker executing proactive check in background via WorkManager.
 */
class ProactiveCompanionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            ProactiveEngagementManager.checkAndDeliver(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("ProactiveWorker", "Error executing proactive check", e)
            Result.retry()
        }
    }
}
