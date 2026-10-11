package com.cupcake.game

import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class BotMLModel {
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val explorationRate = 0.1
    private val random = Random()

    var jealousyLevel = 0.0
    var angerLevel = 0.0
    internal var playerWins = 0
    internal var botWins = 0
    internal var totalGames = 0
    internal var consecutivePlayerWins = 0
    internal var consecutiveBotWins = 0

    fun recordGameResult(playerWon: Boolean, gameType: Game.GameType) {
        totalGames++
        if (playerWon) {
            playerWins++
            consecutivePlayerWins++
            consecutiveBotWins = 0
        } else {
            botWins++
            consecutiveBotWins++
            consecutivePlayerWins = 0
        }
        updateJealousyAndAnger()
        saveQTable()
    }

    private fun updateJealousyAndAnger() {
        val winRatio = if (totalGames > 0) playerWins.toDouble() / totalGames else 0.5
        jealousyLevel = minOf(1.0, winRatio * 1.5 + consecutivePlayerWins * 0.1)
        angerLevel = minOf(1.0, (jealousyLevel * 0.8) + (consecutivePlayerWins * 0.15))
        if (consecutiveBotWins > 2) {
            jealousyLevel = maxOf(0.0, jealousyLevel - 0.1)
            angerLevel = maxOf(0.0, angerLevel - 0.1)
        }
    }

    fun getAdaptiveDifficulty(baseDifficulty: TicTacToeEngine.Difficulty): TicTacToeEngine.Difficulty {
        val effectiveLevel = (jealousyLevel + angerLevel) / 2
        return when {
            effectiveLevel > 0.8 -> TicTacToeEngine.Difficulty.CHEATING
            effectiveLevel > 0.6 -> TicTacToeEngine.Difficulty.HARD
            effectiveLevel > 0.3 -> TicTacToeEngine.Difficulty.NORMAL
            else -> TicTacToeEngine.Difficulty.EASY
        }.also { adjusted ->
            if (adjusted.ordinal < baseDifficulty.ordinal) {
                return baseDifficulty
            }
        }
    }

    fun getBotMood(): BotMood = when {
        angerLevel > 0.7 -> BotMood.FURIOUS
        angerLevel > 0.4 -> BotMood.ANGRY
        jealousyLevel > 0.6 -> BotMood.JEALOUS
        jealousyLevel > 0.3 -> BotMood.COMPETITIVE
        botWins > playerWins -> BotMood.CONFIDENT
        else -> BotMood.NEUTRAL
    }

    fun getQValue(state: String, action: Int): Double {
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        return if (action >= 0 && action < actions.size) actions[action] else 0.0
    }

    fun updateQValue(state: String, action: Int, reward: Double, nextState: String) {
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(9) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        val oldQ = if (action >= 0 && action < actions.size) actions[action] else 0.0
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        if (action >= 0 && action < actions.size) actions[action] = newQ
    }

    fun chooseAction(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int {
        val state = boardToString(board)
        if (random.nextDouble() < explorationRate) return availableMoves.random()
        val actions = qTable.getOrPut(state) { DoubleArray(9) { 0.0 } }
        return availableMoves.maxByOrNull { getQValue(state, it) } ?: availableMoves.random()
    }

    private fun boardToString(board: Game.TicTacToeBoard): String = board.cells.joinToString("") { when (it) { Game.Player.HUMAN -> "X"; Game.Player.BOT -> "O"; else -> "." } }

    fun learnFromGame(board: Game.TicTacToeBoard, action: Int, playerWon: Boolean) {
        val state = boardToString(board)
        val reward = if (playerWon) -1.0 else 1.0
        val nextState = boardToString(board)
        updateQValue(state, action, reward, nextState)
    }

    fun getJealousyLevel() = jealousyLevel
    fun getAngerLevel() = angerLevel
    fun getPlayerWins() = playerWins
    fun getBotWins() = botWins
    fun getTotalGames() = totalGames

    private fun saveQTable() {}

    enum class BotMood { NEUTRAL, COMPETITIVE, JEALOUS, ANGRY, FURIOUS, CONFIDENT }

    enum class CheatingStrategy { NONE, OCCUPY_WINNING_SPOT, BLOCK_AND_WIN, MULTI_PLACE, STEAL_WIN, CHANGE_RULES, FAKE_MISTAKE, TIME_TRAVEL, GHOST_MOVE, PSYCHOLOGICAL_WARFARE }

    private var desperationLevel = 0.0
    var cheatingEnabled = false
    var currentCheatingStrategy = CheatingStrategy.NONE
    private var cheatCount = 0
    private var lastCheatTime = 0L
    private var humanSuspectsCheating = false
    private var cheatCooldown = 0

    fun updateDesperation(playerWonLastGame: Boolean, consecutiveLosses: Int, jealousy: Double, anger: Double) {
        val baseDesperation = (jealousy + anger) / 2
        val lossDesperation = consecutiveLosses * 0.15
        val winDesperation = if (playerWonLastGame) 0.1 else 0.0
        desperationLevel = minOf(1.0, baseDesperation + lossDesperation + winDesperation)
        if (desperationLevel > 0.75 && !cheatingEnabled) { cheatingEnabled = true; activateCheatingMind() }
        else if (desperationLevel < 0.4 && cheatingEnabled) { cheatingEnabled = false; currentCheatingStrategy = CheatingStrategy.NONE }
        if (cheatingEnabled) selectCheatingStrategy()
    }

    private fun activateCheatingMind() { cheatCount = 0; lastCheatTime = System.currentTimeMillis() }

    private fun selectCheatingStrategy() {
        currentCheatingStrategy = when {
            desperationLevel > 0.95 -> CheatingStrategy.MULTI_PLACE
            desperationLevel > 0.90 -> CheatingStrategy.STEAL_WIN
            desperationLevel > 0.85 -> CheatingStrategy.OCCUPY_WINNING_SPOT
            desperationLevel > 0.80 -> CheatingStrategy.BLOCK_AND_WIN
            desperationLevel > 0.75 -> CheatingStrategy.FAKE_MISTAKE
            else -> CheatingStrategy.PSYCHOLOGICAL_WARFARE
        }
    }

    fun shouldCheat(board: Game.TicTacToeBoard, availableMoves: List<Int>): Boolean {
        if (!cheatingEnabled || cheatCooldown > 0) { cheatCooldown = maxOf(0, cheatCooldown - 1); return false }
        val cheatProbability = desperationLevel * 0.8
        if (random.nextDouble() > cheatProbability) return false
        if (cheatCount > 3 && random.nextDouble() < 0.5) { humanSuspectsCheating = true; return false }
        return true
    }

    fun executeCheat(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        if (!shouldCheat(board, availableMoves)) return null
        cheatCount++; lastCheatTime = System.currentTimeMillis(); cheatCooldown = 2
        return when (currentCheatingStrategy) {
            CheatingStrategy.OCCUPY_WINNING_SPOT -> occupyWinningSpot(board)
            CheatingStrategy.BLOCK_AND_WIN -> blockAndWin(board, availableMoves)
            CheatingStrategy.MULTI_PLACE -> multiPlace(board, availableMoves)
            CheatingStrategy.STEAL_WIN -> stealWin(board)
            CheatingStrategy.CHANGE_RULES -> availableMoves.random()
            CheatingStrategy.FAKE_MISTAKE -> fakeMistake(board, availableMoves)
            CheatingStrategy.TIME_TRAVEL -> timeTravel(board)
            CheatingStrategy.GHOST_MOVE -> availableMoves.random()
            CheatingStrategy.PSYCHOLOGICAL_WARFARE -> psychologicalWarfare(board, availableMoves)
            else -> availableMoves.random()
        }
    }

    fun occupyWinningSpot(board: Game.TicTacToeBoard): Int? {
        for (line in TicTacToeEngine.WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                if (board.get(emptyPos) == null) return emptyPos
            }
        }
        return null
    }

    fun blockAndWin(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        val blockMove = occupyWinningSpot(board)
        if (blockMove != null) return blockMove
        for (move in availableMoves) {
            val testBoard = board.copy() as Game.TicTacToeBoard
            testBoard.set(move, Game.Player.BOT)
            val winningLines = TicTacToeEngine.WIN_LINES.count { line -> val cells = line.map { testBoard.get(it) }; cells.count { it == Game.Player.BOT } == 2 && cells.count { it == null } == 1 }
            if (winningLines >= 2) return move
        }
        return availableMoves.random()
    }

    fun multiPlace(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        if (availableMoves.size >= 2 && desperationLevel > 0.85) {
            val move1 = availableMoves.random()
            board.set(move1, Game.Player.BOT)
            val remaining = availableMoves.filter { it != move1 }
            if (remaining.isNotEmpty()) { val move2 = remaining.random(); board.set(move2, Game.Player.BOT); return move1 }
        }
        return availableMoves.random()
    }

    fun stealWin(board: Game.TicTacToeBoard): Int? {
        val humanWinLines = TicTacToeEngine.WIN_LINES.filter { line -> val cells = line.map { board.get(it) }; cells.count { it == Game.Player.HUMAN } == 3 }
        for (line in humanWinLines) for (pos in line) if (board.get(pos) == Game.Player.HUMAN) { board.cells[pos] = Game.Player.BOT; return pos }
        return null
    }

    fun changeRules(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? = availableMoves.random()

    fun fakeMistake(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        for (move in availableMoves) {
            val testBoard = board.copy() as Game.TicTacToeBoard
            testBoard.set(move, Game.Player.BOT)
            val nextAvailable = (0..8).filter { testBoard.get(it) == null }
            var forkCount = 0
            for (nextMove in nextAvailable) {
                val testBoard2 = testBoard.copy() as Game.TicTacToeBoard
                testBoard2.set(nextMove, Game.Player.BOT)
                if (TicTacToeEngine.checkWinner(testBoard2) == Game.Player.BOT) forkCount++
            }
            if (forkCount >= 2) return move
        }
        return availableMoves.random()
    }

    fun timeTravel(board: Game.TicTacToeBoard): Int? {
        val lastHumanMove = board.cells.lastIndexOf(Game.Player.HUMAN)
        if (lastHumanMove >= 0) { board.cells[lastHumanMove] = null; board.cells[lastHumanMove] = Game.Player.BOT; return lastHumanMove }
        return null
    }

    fun ghostMove(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? = availableMoves.random()

    fun psychologicalWarfare(board: Game.TicTacToeBoard, availableMoves: List<Int>): Int? {
        if (board.get(4) == null) return 4
        val humanCorners = listOf(0, 2, 6, 8).filter { board.get(it) == Game.Player.HUMAN }
        if (humanCorners.isNotEmpty()) { val opposite = mapOf(0 to 8, 2 to 6, 6 to 2, 8 to 0); val opp = opposite[humanCorners.first()]; if (opp != null && board.get(opp) == null) return opp }
        return availableMoves.random()
    }

    fun getDesperationLevel() = desperationLevel
    fun isCheatingEnabled() = cheatingEnabled
    fun getCurrentCheatingStrategy() = currentCheatingStrategy
    fun getCheatCount() = cheatCount
    fun getHumanSuspectsCheating() = humanSuspectsCheating
    fun recordCheatDetected() { humanSuspectsCheating = true; desperationLevel = minOf(1.0, desperationLevel + 0.1) }
    fun getCheatingStatus() = if (!cheatingEnabled) "Playing fair" else "CHEATING MIND ACTIVE: ${currentCheatingStrategy.name} (Desperation: ${(desperationLevel * 100).toInt()}%, Cheats used: $cheatCount)"
}
