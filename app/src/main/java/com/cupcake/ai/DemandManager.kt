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
import com.cupcake.game.PointsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Something Marin wants, what it costs to satisfy, and how she reacts either way. */
data class MarinDemand(
    val id: String,
    val title: String,
    val message: String,
    val cost: Long,
    val affection: Int,
    val sulk: Int,
    val fulfilledLine: String,
    val ignoredLine: String
)

/**
 * Marin periodically pings the user with a small want ("I'm hungry!"). Satisfying it
 * spends spendable coins and raises affection; ignoring it makes her sulk a little.
 *
 * Kept as a process-wide object (like [ProactiveEngagementManager]) so both the UI and
 * background notification actions can reach it, with SharedPreferences as the source
 * of truth for affection and the pending demand.
 */
object DemandManager {
    private const val TAG = "DemandManager"
    internal const val PREFS = "cupcake_marin_demands"
    internal const val KEY_AFFECTION = "affection"
    internal const val KEY_LAST_ID = "last_demand_id"
    internal const val KEY_PENDING_ID = "pending_demand_id"
    internal const val KEY_PENDING_TIME = "pending_demand_time"
    private const val WORK_NAME = "marin_demand_worker"

    /** A demand older than this is considered ignored and is silently dropped. */
    private const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L

    private val CATALOG = listOf(
        MarinDemand(
            id = "hungry",
            title = "Marin is hungry 🍰",
            message = "Gojo-kun, I'm starving over here! Buy me a strawberry shortcake? 🍰🥺",
            cost = 50,
            affection = 10,
            sulk = 4,
            fulfilledLine = "Yaaay! Strawberry cake! You're the best, Gojo-kun~ 🍰💕",
            ignoredLine = "No cake...? Hmph. I'll remember that. 😤"
        ),
        MarinDemand(
            id = "snack",
            title = "Marin wants a snack 🍫",
            message = "I smell chocolate somewhere and now I can't stop thinking about it. Help?! 🍫😩",
            cost = 30,
            affection = 7,
            sulk = 3,
            fulfilledLine = "Chocolate!! Okay okay, you're officially my favorite person today~ 🍫😋",
            ignoredLine = "Fine... I'll just go snack on my own feelings then. 😔"
        ),
        MarinDemand(
            id = "attention",
            title = "Marin wants attention 💕",
            message = "Hellooo? Earth to Gojo-kun? I'm right here and I'm BORED. Pay attention to me! 💕",
            cost = 20,
            affection = 6,
            sulk = 5,
            fulfilledLine = "Hehe~ knew you couldn't resist me! Come here, tell me about your day! 💕",
            ignoredLine = "You're ignoring me again... do I really have to fight for your attention? 😾"
        ),
        MarinDemand(
            id = "cosplay",
            title = "Marin has a cosplay idea 👗",
            message = "I found the CUTEST fabric for a new cosplay! Sponsor me and I'll do a photoshoot just for you! 👗✨",
            cost = 80,
            affection = 15,
            sulk = 6,
            fulfilledLine = "Yes!! I'm already sketching it! You won't BELIEVE how it'll turn out! 👗✨💖",
            ignoredLine = "My cosplay dream... crushed... by your empty wallet. 💔"
        ),
        MarinDemand(
            id = "rematch",
            title = "Marin demands a rematch 🎮",
            message = "I KNOW I can beat you this time! Stamina restored, wallet ready — fight me! 🎮😤",
            cost = 40,
            affection = 9,
            sulk = 3,
            fulfilledLine = "Yosh! Get ready to lose, Gojo-kun! 🎮✨",
            ignoredLine = "Scared to lose again, huh? Typical. 😏"
        ),
        MarinDemand(
            id = "sleepy",
            title = "Marin is sleepy 🌙",
            message = "It's wayyy past my bedtime... but I can't sleep without hearing from you first. 🌙🥺",
            cost = 15,
            affection = 5,
            sulk = 2,
            fulfilledLine = "Aww, thank you~ Goodnight, Gojo-kun. Sweet dreams of me! 🌙💤",
            ignoredLine = "Guess I'll just stare at the ceiling then... goodnight, I guess. 🌙"
        )
    )

    private val _currentDemand = MutableStateFlow<MarinDemand?>(null)
    val currentDemand: StateFlow<MarinDemand?> = _currentDemand.asStateFlow()

    private val _affection = MutableStateFlow(0)
    val affection: StateFlow<Int> = _affection.asStateFlow()

    private val _event = MutableStateFlow<String?>(null)
    /** Last fulfil/ignore outcome text, for an in-app snackbar/banner to consume. */
    val lastEvent: StateFlow<String?> = _event.asStateFlow()

    @Volatile
    private var loaded = false

    @Volatile
    private var appContext: Context? = null

    // ---- Lifecycle ---------------------------------------------------------------

    fun initialize(context: Context) {
        appContext = context.applicationContext
        ensureLoaded(context)
        _currentDemand.value = pendingDemand(context)
        DemandNotifier.ensureChannel(context)
        scheduleNext(context, replaceExisting = false)
        Log.d(TAG, "Initialized (affection=${_affection.value}, pending=${_currentDemand.value?.id})")
    }

    // UI convenience wrappers using the context captured at initialize().
    fun fulfillFromUi(): Boolean = appContext?.let { fulfill(it) } ?: false
    fun dismissFromUi(): Unit = appContext?.let { dismiss(it) } ?: Unit
    fun rollFromUi(): MarinDemand? = appContext?.let { rollDemand(it) }

    /** Syncs in-memory affection from prefs (needed when a receiver runs cold). */
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _affection.value = prefs(context).getInt(KEY_AFFECTION, 0)
            loaded = true
        }
    }

    fun scheduleNext(context: Context, replaceExisting: Boolean) {
        val delayMinutes = Random.nextLong(45L, 150L)
        val request = OneTimeWorkRequestBuilder<DemandWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        Log.d(TAG, "Next demand check in $delayMinutes min")
    }

    // ---- Demand flow -------------------------------------------------------------

    /** Picks a new demand (never repeating the previous one) and persists it. */
    fun rollDemand(context: Context): MarinDemand {
        ensureLoaded(context)
        val lastId = prefs(context).getString(KEY_LAST_ID, null)
        val demand = pickDemand(lastId)
        prefs(context).edit()
            .putString(KEY_LAST_ID, demand.id)
            .putString(KEY_PENDING_ID, demand.id)
            .putLong(KEY_PENDING_TIME, System.currentTimeMillis())
            .apply()
        _currentDemand.value = demand
        return demand
    }

    fun byId(id: String): MarinDemand? = CATALOG.firstOrNull { it.id == id }

    /** All demand definitions (exposed for tests / a future "demand log" screen). */
    internal fun catalog(): List<MarinDemand> = CATALOG

    /** Chooses a demand different from [excludeId] (pure, testable). */
    internal fun pickDemand(excludeId: String?, rng: Random = Random): MarinDemand {
        val pool = CATALOG.filter { it.id != excludeId }
        return (if (pool.isEmpty()) CATALOG else pool).random(rng)
    }

    /** Affection never drops below zero. */
    internal fun affectionAfter(current: Int, delta: Int): Int = (current + delta).coerceAtLeast(0)

    /** Pays for the currently pending demand, if any. */
    fun fulfill(context: Context): Boolean {
        val demand = _currentDemand.value ?: return false
        return fulfill(context, demand)
    }

    /**
     * Pays for [demand] with coins. Returns false when the balance is too low,
     * leaving the demand pending so the user can earn more and come back.
     */
    fun fulfill(context: Context, demand: MarinDemand): Boolean {
        ensureLoaded(context)
        val paid = PointsManager.get()?.spendPoints(demand.cost)
            ?: PointsManager.spendPersisted(context, demand.cost)
        if (!paid) {
            Log.d(TAG, "Cannot fulfil '${demand.id}': not enough coins for ${demand.cost}")
            return false
        }
        _affection.value += demand.affection
        persistAffection(context)
        clearPending(context)
        _currentDemand.value = null
        _event.value = demand.fulfilledLine
        DemandNotifier.cancel(context)
        appendChat(context, demand.fulfilledLine)
        Log.i(TAG, "Fulfilled '${demand.id}' for ${demand.cost}; affection=${_affection.value}")
        return true
    }

    fun dismiss(context: Context, penalize: Boolean = true) {
        val demand = _currentDemand.value ?: return
        dismiss(context, demand, penalize)
    }

    fun dismiss(context: Context, demand: MarinDemand, penalize: Boolean = true) {
        ensureLoaded(context)
        if (penalize) {
            _affection.value = affectionAfter(_affection.value, -demand.sulk)
            persistAffection(context)
        }
        clearPending(context)
        _currentDemand.value = null
        _event.value = demand.ignoredLine
        DemandNotifier.cancel(context)
        appendChat(context, demand.ignoredLine)
        Log.i(TAG, "Ignored '${demand.id}'; affection=${_affection.value}")
    }

    fun consumeEvent() { _event.value = null }

    /** Worker entry point: expire stale wants, then maybe raise a fresh one. */
    fun pollDemand(context: Context) {
        ensureLoaded(context)
        val pending = _currentDemand.value ?: pendingDemand(context)?.also { _currentDemand.value = it }
        if (pending != null) {
            val age = System.currentTimeMillis() - prefs(context).getLong(KEY_PENDING_TIME, System.currentTimeMillis())
            if (age < STALE_AFTER_MS) {
                Log.d(TAG, "Demand '${pending.id}' still pending; not raising another.")
                scheduleNext(context, replaceExisting = true)
                return
            }
            dismiss(context, pending, penalize = true)
        }

        val demand = rollDemand(context)
        appendChat(context, demand.message)
        DemandNotifier.notifyDemand(context, demand)
        scheduleNext(context, replaceExisting = true)
    }

    // ---- Internals ---------------------------------------------------------------

    private fun pendingDemand(context: Context): MarinDemand? {
        val id = prefs(context).getString(KEY_PENDING_ID, null) ?: return null
        val age = System.currentTimeMillis() - prefs(context).getLong(KEY_PENDING_TIME, System.currentTimeMillis())
        if (age >= STALE_AFTER_MS) {
            clearPending(context)
            return null
        }
        return byId(id)
    }

    private fun clearPending(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_ID).remove(KEY_PENDING_TIME).apply()
    }

    private fun persistAffection(context: Context) {
        prefs(context).edit().putInt(KEY_AFFECTION, _affection.value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun appendChat(context: Context, text: String) {
        try {
            val message = ChatMessage.assistant(
                content = text,
                conversationId = "chat_marin_kitagawa",
                modelUsed = "marin_demand"
            )
            val store = ChatSessionStore.get()
            if (store != null) {
                store.addMessage("marin_kitagawa", message)
            } else {
                kotlinx.coroutines.runBlocking {
                    AppDatabase.getInstance(context).chatMessageDao().insert(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append demand chat line", e)
        }
    }
}

/** Background trigger that raises a new demand when due. */
class DemandWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            DemandManager.pollDemand(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("DemandWorker", "Demand poll failed", e)
            Result.retry()
        }
    }
}

/** Notification channel + Fulfill/Ignore actions for a pending demand. */
object DemandNotifier {
    internal const val CHANNEL_ID = "marin_demands"
    internal const val NOTIFICATION_ID = 2002
    internal const val ACTION_FULFILL = "com.cupcake.action.FULFILL_DEMAND"
    internal const val ACTION_IGNORE = "com.cupcake.action.IGNORE_DEMAND"
    internal const val EXTRA_DEMAND_ID = "demand_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Marin's Demands",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Marin asking for snacks, attention and rematches (spends points)"
                        enableVibration(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    fun notifyDemand(context: Context, demand: MarinDemand) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("characterId", "marin_kitagawa")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fulfill = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, DemandActionReceiver::class.java).apply {
                action = ACTION_FULFILL
                putExtra(EXTRA_DEMAND_ID, demand.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ignore = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, DemandActionReceiver::class.java).apply {
                action = ACTION_IGNORE
                putExtra(EXTRA_DEMAND_ID, demand.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(demand.title)
            .setContentText(demand.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(demand.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Fulfill (${demand.cost} pts)", fulfill)
            .addAction(0, "Ignore", ignore)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(NOTIFICATION_ID, notification)
    }

    fun notifyResult(context: Context, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Marin 💕")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(NOTIFICATION_ID + 1, notification)
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(NOTIFICATION_ID)
    }
}
