package com.cupcake.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointsManager @Inject constructor() {

    private val _points = MutableStateFlow(0L)
    val points: MutableStateFlow<Long> = _points

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

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        // TODO: Load from DataStore/SharedPreferences
    }

    private fun saveToPrefs() {
        // TODO: Save to DataStore/SharedPreferences
    }

    fun awardPoints(basePoints: Int, gameType: Game.GameType, won: Boolean) {
        val multiplier = when {
            won -> 1.5
            else -> 0.5
        }
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

        val earned = (basePoints * multiplier * difficultyBonus).toLong()
        val newTotal = _points.value + earned
        _points.value = newTotal

        _totalGamesPlayed.value++
        if (won) _totalWins.value++

        // Update game stats
        val currentStats = _gameStats.value[gameType] ?: GameStats(gameType = gameType)
        val updatedStats = currentStats.copy(
            gamesPlayed = currentStats.gamesPlayed + 1,
            wins = currentStats.wins + if (won) 1 else 0,
            totalPoints = currentStats.totalPoints + earned,
            bestScore = maxOf(currentStats.bestScore, earned.toInt())
        )
        _gameStats.value = _gameStats.value + (gameType to updatedStats)

        // Check level up
        checkLevelUp()

        // Check achievements
        checkAchievements(gameType, won, earned)

        saveToPrefs()
    }

    private fun checkLevelUp() {
        val currentLevel = _level.value
        val requiredXp = getXpForLevel(currentLevel + 1)
        if (_points.value >= requiredXp) {
            _level.value = currentLevel + 1
            _xpProgress.value = 0f
            // Trigger level up reaction
        } else {
            val currentXp = getXpForLevel(currentLevel)
            val nextXp = getXpForLevel(currentLevel + 1)
            _xpProgress.value = (_points.value - currentXp).toFloat() / (nextXp - currentXp).toFloat()
        }
    }

    private fun getXpForLevel(level: Int): Long {
        // Exponential curve: 100, 250, 500, 1000, 2000, 4000...
        return (100.0 * Math.pow(1.5, (level - 1).toDouble())).toLong()
    }

    private fun checkAchievements(gameType: Game.GameType, won: Boolean, points: Long) {
        val newAchievements = mutableSetOf<String>()
        newAchievements.addAll(_achievements.value)

        // First win
        if (won && _totalWins.value == 1) {
            newAchievements.add("first_win")
        }

        // Streak achievements
        if (won) {
            val stats = _gameStats.value[gameType]
            if (stats != null) {
                when (stats.wins) {
                    5 -> newAchievements.add("${gameType.name.toLowerCase()}_novice")
                    10 -> newAchievements.add("${gameType.name.toLowerCase()}_expert")
                    25 -> newAchievements.add("${gameType.name.toLowerCase()}_master")
                    50 -> newAchievements.add("${gameType.name.toLowerCase()}_grandmaster")
                    100 -> newAchievements.add("${gameType.name.toLowerCase()}_legend")
                }
            }
        }

        // Total games
        when (_totalGamesPlayed.value) {
            10 -> newAchievements.add("casual_player")
            50 -> newAchievements.add("regular_player")
            100 -> newAchievements.add("dedicated_player")
            500 -> newAchievements.add("hardcore_player")
            1000 -> newAchievements.add("legendary_player")
        }

        // Points milestones
        when (_points.value) {
            in 1000..9999 -> newAchievements.add("point_collector")
            in 10000..99999 -> newAchievements.add("point_hoarder")
            in 100000..999999 -> newAchievements.add("point_millionaire")
        }

        // Perfect game achievements (won without losing)
        val stats = _gameStats.value[gameType]
        if (won && stats != null && stats.wins == stats.gamesPlayed && stats.gamesPlayed >= 5) {
            newAchievements.add("${gameType.name.toLowerCase()}_perfect")
        }

        // Try all games
        if (_gameStats.value.size >= 10) {
            newAchievements.add("polymath")
        }

        if (newAchievements != _achievements.value) {
            _achievements.value = newAchievements
        }
    }

    fun getLevelProgress(): Pair<Long, Long> {
        val currentXp = getXpForLevel(_level.value)
        val nextXp = getXpForLevel(_level.value + 1)
        return Pair(_points.value - currentXp, nextXp - currentXp)
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
}