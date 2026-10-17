package com.cupcake.game

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the two economies:
 *  - [points] are **spendable coins** (stake for Marin's demands),
 *  - [xp] is a **lifetime** score that only ever grows and drives [level].
 *
 * Keeping them separate means fulfilling a demand never knocks you back a level.
 * State is persisted through [PointsStore] so it survives process death.
 */
@Singleton
class PointsManager @Inject constructor(
    private val store: PointsStore
) {

    /** Convenience constructor for tests / previews: in-memory, no persistence. */
    constructor() : this(InMemoryPointsStore())

    private val _points = MutableStateFlow(0L)
    val points: MutableStateFlow<Long> = _points

    private val _xp = MutableStateFlow(0L)
    val xp: MutableStateFlow<Long> = _xp

    private val _level = MutableStateFlow(1)
    val level: MutableStateFlow<Int> = _level

    private val _xpProgress = MutableStateFlow(0f)
    val xpProgress: MutableStateFlow<Float> = _xpProgress

    private val _totalGamesPlayed = MutableStateFlow(0)
    val totalGamesPlayed: MutableStateFlow<Int> = _totalGamesPlayed

    private val _totalWins = MutableStateFlow(0)
    val totalWins: MutableStateFlow<Int> = _totalWins

    private val _gameStats = MutableStateFlow<Map<Game.GameType, GameStats>>(emptyMap())
    val gameStats: MutableStateFlow<Map<Game.GameType, GameStats>> = _gameStats

    private val _achievements = MutableStateFlow<Set<String>>(emptySet())
    val achievements: MutableStateFlow<Set<String>> = _achievements

    private val _levelUpEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val levelUpEvents: SharedFlow<Int> = _levelUpEvents.asSharedFlow()

    private val statsLock = Any()

    init {
        load()
        instance = this
    }

    // ---- Persistence -------------------------------------------------------------

    private fun load() {
        val snapshot = runCatching { store.load() }.getOrElse {
            Log.e(TAG, "Failed to load points", it)
            PointsSnapshot()
        }
        _points.value = snapshot.points
        _xp.value = snapshot.xp
        _level.value = levelFromXp(snapshot.xp)
        _xpProgress.value = progressInLevel(snapshot.xp, _level.value)
        _totalGamesPlayed.value = snapshot.totalGamesPlayed
        _totalWins.value = snapshot.totalWins
        _achievements.value = snapshot.achievements.toSet()
        _gameStats.value = snapshot.stats.mapNotNull { stored ->
            val type = runCatching { Game.GameType.valueOf(stored.gameType) }.getOrNull()
                ?: return@mapNotNull null
            type to GameStats(
                gameType = type,
                gamesPlayed = stored.gamesPlayed,
                wins = stored.wins,
                totalPoints = stored.totalPoints,
                bestScore = stored.bestScore
            )
        }.toMap()
    }

    private fun persist() {
        runCatching {
            store.save(
                PointsSnapshot(
                    points = _points.value,
                    xp = _xp.value,
                    level = _level.value,
                    totalGamesPlayed = _totalGamesPlayed.value,
                    totalWins = _totalWins.value,
                    stats = synchronized(statsLock) {
                        _gameStats.value.map { (type, s) ->
                            StoredGameStat(type.name, s.gamesPlayed, s.wins, s.totalPoints, s.bestScore)
                        }
                    },
                    achievements = _achievements.value.toList()
                )
            )
        }.onFailure { Log.e(TAG, "Failed to persist points", it) }
    }

    // ---- Earning / spending ------------------------------------------------------

    fun awardPoints(basePoints: Int, gameType: Game.GameType, won: Boolean) {
        val multiplier = if (won) 1.5 else 0.5
        val difficultyBonus = when (gameType) {
            Game.GameType.CHESS -> 2.0
            Game.GameType.CONNECT_FOUR -> 1.5
            Game.GameType.MASTERMIND -> 1.5
            Game.GameType.TIC_TAC_TOE_4X4 -> 1.3
            Game.GameType.MATH_QUIZ -> 1.2
            Game.GameType.NUMBER_BASE -> 1.2
            Game.GameType.MEMORY_MATCH -> 1.1
            Game.GameType.PING_PONG -> 1.3
            else -> 1.0
        }

        val earned = maxOf(1L, (basePoints * multiplier * difficultyBonus).toLong())

        _points.value += earned
        _xp.value += earned

        _totalGamesPlayed.value++
        if (won) _totalWins.value++

        synchronized(statsLock) {
            val current = _gameStats.value[gameType] ?: GameStats(gameType = gameType)
            _gameStats.value = _gameStats.value + (gameType to current.copy(
                gamesPlayed = current.gamesPlayed + 1,
                wins = current.wins + if (won) 1 else 0,
                totalPoints = current.totalPoints + earned,
                bestScore = maxOf(current.bestScore, earned.toInt())
            ))
        }

        recomputeLevel()
        checkAchievements(gameType, won)
        persist()
    }

    /**
     * Spend coins. Returns false (and changes nothing) when the balance is too low,
     * so callers can hard-gate features like Marin's demands.
     */
    fun spendPoints(amount: Long): Boolean {
        if (amount <= 0L) return true
        synchronized(statsLock) {
            if (_points.value < amount) return false
            _points.value -= amount
        }
        persist()
        return true
    }

    fun addCoins(amount: Long) {
        if (amount <= 0L) return
        _points.value += amount
        persist()
    }

    // ---- Levels ------------------------------------------------------------------

    private fun recomputeLevel() {
        val xp = _xp.value
        val previous = _level.value
        val current = levelFromXp(xp)
        _level.value = current
        _xpProgress.value = progressInLevel(xp, current)
        if (current > previous) {
            for (lvl in (previous + 1)..current) _levelUpEvents.tryEmit(lvl)
        }
    }

    /** XP needed to *reach* [level] (level 1 costs 0). */
    private fun getXpForLevel(level: Int): Long {
        if (level <= 1) return 0L
        var total = 0L
        var need = BASE_XP
        for (l in 2..level) {
            total += need
            need = (need * GROWTH).toLong()
        }
        return total
    }

    private fun levelFromXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= getXpForLevel(level + 1)) level++
        return level
    }

    private fun progressInLevel(xp: Long, level: Int): Float {
        val floor = getXpForLevel(level)
        val ceiling = getXpForLevel(level + 1)
        val span = (ceiling - floor).coerceAtLeast(1L)
        return ((xp - floor).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun checkAchievements(gameType: Game.GameType, won: Boolean) {
        val newAchievements = _achievements.value.toMutableSet()

        if (won && _totalWins.value == 1) newAchievements.add("first_win")

        if (won) {
            when (_gameStats.value[gameType]?.wins) {
                5 -> newAchievements.add("${gameType.name.lowercase()}_novice")
                10 -> newAchievements.add("${gameType.name.lowercase()}_expert")
                25 -> newAchievements.add("${gameType.name.lowercase()}_master")
                50 -> newAchievements.add("${gameType.name.lowercase()}_grandmaster")
                100 -> newAchievements.add("${gameType.name.lowercase()}_legend")
            }
        }

        when (_totalGamesPlayed.value) {
            10 -> newAchievements.add("casual_player")
            50 -> newAchievements.add("regular_player")
            100 -> newAchievements.add("dedicated_player")
            500 -> newAchievements.add("hardcore_player")
            1000 -> newAchievements.add("legendary_player")
        }

        // Milestones keyed off lifetime XP so spending coins can't re-trigger them.
        when (_xp.value) {
            in 1_000..9_999 -> newAchievements.add("point_collector")
            in 10_000..99_999 -> newAchievements.add("point_hoarder")
            in 100_000..999_999 -> newAchievements.add("point_millionaire")
        }

        val stats = _gameStats.value[gameType]
        if (won && stats != null && stats.wins == stats.gamesPlayed && stats.gamesPlayed >= 5) {
            newAchievements.add("${gameType.name.lowercase()}_perfect")
        }

        if (_gameStats.value.size >= 10) newAchievements.add("polymath")

        if (newAchievements != _achievements.value) {
            _achievements.value = newAchievements
        }
    }

    /** XP accrued inside the current level and the XP span of that level. */
    fun getLevelProgress(): Pair<Long, Long> {
        val level = _level.value
        val floor = getXpForLevel(level)
        val ceiling = getXpForLevel(level + 1)
        return Pair((_xp.value - floor).coerceAtLeast(0L), (ceiling - floor).coerceAtLeast(1L))
    }

    fun getWinRate(gameType: Game.GameType): Double {
        val stats = _gameStats.value[gameType] ?: return 0.0
        return if (stats.gamesPlayed > 0) stats.wins.toDouble() / stats.gamesPlayed else 0.0
    }

    data class GameStats(
        val gameType: Game.GameType,
        var gamesPlayed: Int = 0,
        var wins: Int = 0,
        var totalPoints: Long = 0,
        var bestScore: Int = 0
    )

    companion object {
        private const val TAG = "PointsManager"
        private const val BASE_XP = 100L
        private const val GROWTH = 1.5
        private const val MAX_LEVEL = 99

        @Volatile
        private var instance: PointsManager? = null

        /** Process-wide instance for non-Hilt callers (e.g. notification receivers). */
        fun get(): PointsManager? = instance

        /**
         * Spends coins even from a cold start (notification tap before any screen
         * injected the manager). Routes through the live instance when available so
         * in-memory state stays in sync.
         */
        fun spendPersisted(context: android.content.Context, amount: Long): Boolean {
            instance?.let { return it.spendPoints(amount) }
            val store = SharedPrefsPointsStore(context)
            val snapshot = store.load()
            if (snapshot.points < amount) return false
            store.save(snapshot.copy(points = snapshot.points - amount))
            return true
        }
    }
}
