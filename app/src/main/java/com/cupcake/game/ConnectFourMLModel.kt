package com.cupcake.game

import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class ConnectFourMLModel {
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val random = Random()
    
    // Cheating mind for Connect Four
    private var desperationLevel = 0.0
    var cheatingEnabled = false  // Make public
    
    fun chooseAction(board: Game.ConnectFourBoard, availableMoves: List<Int>, jealousyLevel: Double): Int {
        val state = boardToString(board)
        val actions = qTable.getOrPut(state) { DoubleArray(7) { 0.0 } }
        
        // More jealousy = more exploitation (smarter play)
        val explorationRate = maxOf(0.05, 0.2 - jealousyLevel * 0.15)
        
        if (random.nextDouble() < explorationRate) {
            return availableMoves.random()
        }
        
        return availableMoves.maxByOrNull { actions[it] } ?: availableMoves.random()
    }
    
    fun updateQValue(board: Game.ConnectFourBoard, action: Int, reward: Double, nextBoard: Game.ConnectFourBoard) {
        val state = boardToString(board)
        val nextState = boardToString(nextBoard)
        val actions = qTable.getOrPut(state) { DoubleArray(7) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(7) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        
        val oldQ = actions[action]
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        actions[action] = newQ
    }
    
    // Cheating mind methods for Connect Four
    fun updateDesperation(playerWonLastGame: Boolean, consecutiveLosses: Int, jealousy: Double, anger: Double) {
        val baseDesperation = (jealousy + anger) / 2
        val lossDesperation = consecutiveLosses * 0.15
        val winDesperation = if (playerWonLastGame) 0.1 else 0.0
        
        desperationLevel = minOf(1.0, baseDesperation + lossDesperation + winDesperation)
        
        if (desperationLevel > 0.75 && !cheatingEnabled) {
            cheatingEnabled = true
        } else if (desperationLevel < 0.4 && cheatingEnabled) {
            cheatingEnabled = false
        }
    }
    
    fun isCheatingEnabled() = cheatingEnabled
    
    fun executeCheat(board: Game.ConnectFourBoard, availableMoves: List<Int>): Int? {
        if (!cheatingEnabled || desperationLevel < 0.7) return null
        
        val cheatProbability = desperationLevel * 0.5
        if (random.nextDouble() > cheatProbability) return null
        
        // Cheat: Drop two pieces in same column
        if (desperationLevel > 0.85) {
            for (col in availableMoves) {
                var row = 5
                while (row >= 0 && board.get(row * 7 + col) != null) row--
                if (row >= 1) {
                    // Place two pieces in same column
                    board.set(row * 7 + col, Game.Player.BOT)
                    board.set((row - 1) * 7 + col, Game.Player.BOT)
                    return row * 7 + col
                }
            }
        }
        
        // Block human's winning move
        for (col in availableMoves) {
            var row = 5
            while (row >= 0 && board.get(row * 7 + col) != null) row--
            if (row >= 0) {
                val testBoard = board.copy()
                testBoard.set(row * 7 + col, Game.Player.HUMAN)
                if (ConnectFourEngine.checkWinner(testBoard) == Game.Player.HUMAN) {
                    return row * 7 + col
                }
            }
        }
        
        return null
    }
    
    private fun boardToString(board: Game.ConnectFourBoard): String {
        // Access grid through get() since grid is private
        val sb = StringBuilder()
        for (row in 0..5) {
            for (col in 0..6) {
                sb.append(board.get(row * 7 + col)?.symbol ?: ".")
            }
        }
        return sb.toString()
    }
}

// ==========================================
// TicTacToe 4x4 ML Model
// ==========================================