package com.cupcake.game

import android.content.Context

/** Serializable snapshot of everything [PointsManager] owns. */
data class PointsSnapshot(
    val points: Long = 0L,
    val xp: Long = 0L,
    val level: Int = 1,
    val totalGamesPlayed: Int = 0,
    val totalWins: Int = 0,
    val stats: List<StoredGameStat> = emptyList(),
    val achievements: List<String> = emptyList()
)

data class StoredGameStat(
    val gameType: String,
    val gamesPlayed: Int,
    val wins: Int,
    val totalPoints: Long,
    val bestScore: Int
)

/** Persistence boundary for the point system (swappable in tests). */
interface PointsStore {
    fun load(): PointsSnapshot
    fun save(snapshot: PointsSnapshot)
    fun clear()
}

class InMemoryPointsStore(initial: PointsSnapshot = PointsSnapshot()) : PointsStore {
    private var snapshot: PointsSnapshot = initial
    override fun load(): PointsSnapshot = snapshot
    override fun save(snapshot: PointsSnapshot) { this.snapshot = snapshot }
    override fun clear() { snapshot = PointsSnapshot() }
}

/**
 * SharedPreferences-backed store. Deliberately tiny/string-based so we don't pull
 * in another serialization dependency for six fields.
 */
class SharedPrefsPointsStore(private val context: Context) : PointsStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("cupcake_points", Context.MODE_PRIVATE)

    override fun load(): PointsSnapshot {
        val statsRaw = prefs.getString(KEY_STATS, "").orEmpty()
        val stats = statsRaw.split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val f = entry.split('|')
                if (f.size != 5) return@mapNotNull null
                StoredGameStat(
                    gameType = f[0],
                    gamesPlayed = f[1].toIntOrNull() ?: 0,
                    wins = f[2].toIntOrNull() ?: 0,
                    totalPoints = f[3].toLongOrNull() ?: 0L,
                    bestScore = f[4].toIntOrNull() ?: 0
                )
            }
        return PointsSnapshot(
            points = prefs.getLong(KEY_POINTS, 0L),
            xp = prefs.getLong(KEY_XP, 0L),
            level = prefs.getInt(KEY_LEVEL, 1),
            totalGamesPlayed = prefs.getInt(KEY_GAMES, 0),
            totalWins = prefs.getInt(KEY_WINS, 0),
            stats = stats,
            achievements = prefs.getStringSet(KEY_ACHIEVEMENTS, emptySet()).orEmpty().toList()
        )
    }

    override fun save(snapshot: PointsSnapshot) {
        val statsRaw = snapshot.stats.joinToString(";") { s ->
            "${s.gameType}|${s.gamesPlayed}|${s.wins}|${s.totalPoints}|${s.bestScore}"
        }
        prefs.edit()
            .putLong(KEY_POINTS, snapshot.points)
            .putLong(KEY_XP, snapshot.xp)
            .putInt(KEY_LEVEL, snapshot.level)
            .putInt(KEY_GAMES, snapshot.totalGamesPlayed)
            .putInt(KEY_WINS, snapshot.totalWins)
            .putString(KEY_STATS, statsRaw)
            .putStringSet(KEY_ACHIEVEMENTS, snapshot.achievements.toSet())
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_POINTS = "points"
        const val KEY_XP = "xp"
        const val KEY_LEVEL = "level"
        const val KEY_GAMES = "total_games"
        const val KEY_WINS = "total_wins"
        const val KEY_STATS = "stats"
        const val KEY_ACHIEVEMENTS = "achievements"
    }
}
