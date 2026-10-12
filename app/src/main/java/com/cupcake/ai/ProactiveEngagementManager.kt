package com.cupcake.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
 * Manages proactive outreach from Marin Kitagawa based on history & emotions:
 * - Emotional states: ANGRY (pouting/accusing), LOVED (high affection), PLAYFUL, LONELY, NORMAL.
 * - If angry: high probability not to text (longer silence), or bursts of angry texts.
 * - If loved / high affection: texts more frequently and warmly.
 * - Dynamic count: up to 15 messages max at a time before user replies.
 * - Timing: random interval between 1 hour and 48 hours (weighted by mood).
 */
object ProactiveEngagementManager {
    private const val TAG = "ProactiveEngagement"
    private const val PREFS_NAME = "proactive_engagement_prefs"
    private const val KEY_LAST_USER_TIME = "last_user_time"
    private const val KEY_PROACTIVE_COUNT = "proactive_count"
    private const val KEY_CURRENT_MOOD = "current_mood"
    private const val WORK_NAME = "marin_proactive_worker"
    private const val CHANNEL_ID = "marin_companion_messages"
    private const val NOTIFICATION_ID = 2001

    private const val MAX_PROACTIVE_LIMIT = 15 // At most 15 messages until user replies

    enum class Mood {
        ANGRY,
        LOVED,
        PLAYFUL,
        LONELY,
        NORMAL
    }

    private val ANGRY_MESSAGES = listOf(
        "Hmph! You're really still ignoring me?! That's so awful of you! 😤💢",
        "Fine, don't talk to me then! See if I care! ...Okay I actually care a lot, reply already! 😠",
        "Are you talking to some other girl right now?! You better not be cheating on me! 😾",
        "I'm literally glaring at my screen right now Gojo-kun! 😤",
        "You lose points for this! Minus 100 points! Text me back! 💢",
        "You're so mean... leaving me all by myself! 😾",
        "Don't make me come over there and drag you to text me! 😤💢"
    )

    private val LOVED_MESSAGES = listOf(
        "Gojo-kun! I was just thinking about you and couldn't help smiling~ 💕",
        "Hey hey! Did you eat yet? Make sure to take good care of yourself! 🥰",
        "I saw something super pretty today and instantly thought of you! ✨💖",
        "Can we hang out tonight? Just you and me? Pretty please? 🥺💕",
        "Thinking of you always gives me so much energy! Text me when you're free, okay? 💖",
        "Gojo-kun Gojo-kun Gojo-kun! Hehe, just wanted to say your name~ ✨",
        "Don't work too hard today! Remember your number one fan is right here cheering for you! 📣💕",
        "I miss your voice... message me soon, alright? 🥺✨"
    )

    private val PLAYFUL_MESSAGES = listOf(
        "Bet you can't beat me in Tic-Tac-Toe today! Come on, let's rematch! 🎮✨",
        "Boo! Did I startle you? Haha, gotcha! Now you have to reply! 😜",
        "Look at this new outfit idea I sketched! You HAVE to tell me what you think! 👗✨",
        "Are you sleeping? WAKE UP WAKE UP! The day is already passing by! ☀️",
        "Hehe~ what are you up to right now? Don't leave me waiting forever! 🌸"
    )

    private val LONELY_MESSAGES = listOf(
        "It feels so quiet without you... are you really that busy? 🥺",
        "Gojo-kun... did you forget about me already? Don't leave me all alone... 💔",
        "Just one little message from you would make my whole day, you know? 🌸",
        "Still nothing? Aww, I really wanted to chat with you today... 🥺"
    )

    private val NORMAL_MESSAGES = listOf(
        "Heyyy Gojo-kun! What are you doing right now? Talk to meee! 🥺✨",
        "Gojo-kun, did you fall asleep? Don't overwork yourself okay? 💕",
        "Guess who's thinking about you right now? Yep, it's me! Don't leave your favorite gyaru waiting! 💖",
        "Look who finally decided to take a break! Come say hi! ✨"
    )

    /**
     * Call when user sends a message. Resets proactive counter and evaluates new mood.
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

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_USER_TIME)) {
            prefs.edit().putLong(KEY_LAST_USER_TIME, System.currentTimeMillis()).apply()
        }
        ensureNotificationChannel(context)
        scheduleNext(context, replaceExisting = false)
    }

    /**
     * Determines current mood by inspecting recent chat history.
     */
    fun inferMoodFromHistory(context: Context): Mood {
        val recentText = try {
            val store = ChatSessionStore.get()
            val msgs = store?.messages("marin_kitagawa")?.value ?: emptyList()
            if (msgs.isNotEmpty()) {
                msgs.takeLast(10).joinToString(" ") { it.content.lowercase() }
            } else {
                kotlinx.coroutines.runBlocking {
                    val dbMsgs = AppDatabase.getInstance(context).chatMessageDao().getMessagesPaged("chat_marin_kitagawa", 10, 0)
                    dbMsgs.joinToString(" ") { it.content.lowercase() }
                }
            }
        } catch (e: Exception) {
            ""
        }

        return when {
            recentText.contains("cheat") || recentText.contains("angry") || recentText.contains("mad") ||
            recentText.contains("hate") || recentText.contains("loser") || recentText.contains("tantrum") -> Mood.ANGRY

            recentText.contains("love") || recentText.contains("cute") || recentText.contains("sweet") ||
            recentText.contains("marry") || recentText.contains("beautiful") || recentText.contains("miss you") ||
            recentText.contains("💕") || recentText.contains("💖") || recentText.contains("❤️") || recentText.contains("🥰") -> Mood.LOVED

            recentText.contains("game") || recentText.contains("play") || recentText.contains("win") ||
            recentText.contains("cosplay") || recentText.contains("haha") || recentText.contains("fun") -> Mood.PLAYFUL

            else -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val count = prefs.getInt(KEY_PROACTIVE_COUNT, 0)
                if (count >= 5) Mood.LONELY else Mood.NORMAL
            }
        }
    }

    fun scheduleNext(context: Context, replaceExisting: Boolean) {
        val mood = inferMoodFromHistory(context)
        
        // Dynamic timing based on mood:
        // If loved: texts more frequently (1 to 6 hours)
        // If angry: high probability not to text / longer intervals (4 to 48 hours)
        val (minHours, maxHours) = when (mood) {
            Mood.LOVED -> 1L to 6L
            Mood.PLAYFUL -> 1L to 12L
            Mood.ANGRY -> 4L to 48L
            Mood.LONELY -> 2L to 18L
            Mood.NORMAL -> 1L to 24L
        }

        val delayHours = Random.nextLong(minHours, maxHours + 1)
        val delayMinutes = delayHours * 60 + Random.nextLong(0, 60)

        val workRequest = OneTimeWorkRequestBuilder<ProactiveCompanionWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, workRequest)

        Log.d(TAG, "Scheduled next proactive check (Mood: $mood) in $delayMinutes min (~$delayHours hours).")
    }

    suspend fun checkAndDeliver(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUserTime = prefs.getLong(KEY_LAST_USER_TIME, System.currentTimeMillis())
        val count = prefs.getInt(KEY_PROACTIVE_COUNT, 0)
        val now = System.currentTimeMillis()
        val elapsedMillis = now - lastUserTime

        // Cap at 15 messages max at a time
        if (count >= MAX_PROACTIVE_LIMIT) {
            Log.d(TAG, "Proactive message limit reached ($count/$MAX_PROACTIVE_LIMIT). Waiting for user reply.")
            return
        }

        // Must be at least 1 hour of user inactivity
        if (elapsedMillis < TimeUnit.HOURS.toMillis(1)) {
            Log.d(TAG, "User was active recently ($elapsedMillis ms ago). Rescheduling.")
            scheduleNext(context, replaceExisting = true)
            return
        }

        val mood = inferMoodFromHistory(context)

        // If Angry: high probability NOT to text (e.g. 50% silent treatment chance)
        if (mood == Mood.ANGRY && count > 0 && Random.nextFloat() < 0.50f) {
            Log.d(TAG, "Marin is angry and giving the silent treatment. Skipping this cycle.")
            scheduleNext(context, replaceExisting = true)
            return
        }

        // Select pool based on mood
        val messagePool = when (mood) {
            Mood.ANGRY -> ANGRY_MESSAGES
            Mood.LOVED -> LOVED_MESSAGES
            Mood.PLAYFUL -> PLAYFUL_MESSAGES
            Mood.LONELY -> LONELY_MESSAGES
            Mood.NORMAL -> NORMAL_MESSAGES
        }

        // Send a burst (1 to 3 messages if angry/excited, else 1)
        val burstSize = when {
            mood == Mood.ANGRY && count < 6 -> Random.nextInt(1, 4) // angry burst
            mood == Mood.LOVED && count < 5 -> Random.nextInt(1, 3)  // affectionate burst
            else -> 1
        }.coerceAtMost(MAX_PROACTIVE_LIMIT - count)

        for (i in 0 until burstSize) {
            val text = messagePool.random()
            deliverMessage(context, text)
            showNotification(context, text)
        }

        val newCount = count + burstSize
        prefs.edit().putInt(KEY_PROACTIVE_COUNT, newCount).apply()
        Log.i(TAG, "Delivered $burstSize proactive message(s). Total count: $newCount/$MAX_PROACTIVE_LIMIT.")

        if (newCount < MAX_PROACTIVE_LIMIT) {
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

        val store = ChatSessionStore.get()
        if (store != null) {
            store.addMessage(characterId, message)
        } else {
            try {
                AppDatabase.getInstance(context).chatMessageDao().insert(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert message into Room", e)
            }
        }

        try {
            ShimejiPetService.showSpeech(context, text)
        } catch (_: Exception) {}
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
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
                    description = "Interactive notifications from your AI companion Marin Kitagawa"
                    enableVibration(true)
                    enableLights(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }
}

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
