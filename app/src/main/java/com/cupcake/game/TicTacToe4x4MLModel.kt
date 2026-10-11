package com.cupcake.game

import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class TicTacToe4x4MLModel {
    private val qTable = ConcurrentHashMap<String, DoubleArray>()
    private val learningRate = 0.1
    private val discountFactor = 0.9
    private val random = Random()
    
    // Cheating mind for 4x4
    private var desperationLevel = 0.0
    private var cheatingEnabled = false
    
    fun chooseAction(board: Game.TicTacToe4x4Board, availableMoves: List<Int>, jealousyLevel: Double): Int {
        val state = boardToString(board)
        val actions = qTable.getOrPut(state) { DoubleArray(16) { 0.0 } }
        
        val explorationRate = maxOf(0.05, 0.2 - jealousyLevel * 0.15)
        
        if (random.nextDouble() < explorationRate) {
            return availableMoves.random()
        }
        
        return availableMoves.maxByOrNull { actions[it] } ?: availableMoves.random()
    }
    
    fun updateQValue(board: Game.TicTacToe4x4Board, action: Int, reward: Double, nextBoard: Game.TicTacToe4x4Board) {
        val state = boardToString(board)
        val nextState = boardToString(nextBoard)
        val actions = qTable.getOrPut(state) { DoubleArray(16) { 0.0 } }
        val nextActions = qTable.getOrPut(nextState) { DoubleArray(16) { 0.0 } }
        val maxNextQ = nextActions.maxOrNull() ?: 0.0
        
        val oldQ = actions[action]
        val newQ = oldQ + learningRate * (reward + discountFactor * maxNextQ - oldQ)
        actions[action] = newQ
    }
    
    // Cheating mind methods
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
    
    fun executeCheat(board: Game.TicTacToe4x4Board, availableMoves: List<Int>): Int? {
        if (!cheatingEnabled || desperationLevel < 0.7) return null
        
        val cheatProbability = desperationLevel * 0.6
        if (random.nextDouble() > cheatProbability) return null
        
        // Simple cheat: place two pieces
        if (availableMoves.size >= 2 && desperationLevel > 0.85) {
            val move1 = availableMoves.random()
            board.set(move1, Game.Player.BOT)
            val remaining = availableMoves.filter { it != move1 }
            if (remaining.isNotEmpty()) {
                val move2 = remaining.random()
                board.set(move2, Game.Player.BOT)
                return move1
            }
        }
        
        // Occupy winning spot
        val winLines = listOf(
            intArrayOf(0,1,2,3), intArrayOf(4,5,6,7), intArrayOf(8,9,10,11), intArrayOf(12,13,14,15), // rows
            intArrayOf(0,4,8,12), intArrayOf(1,5,9,13), intArrayOf(2,6,10,14), intArrayOf(3,7,11,15), // cols
            intArrayOf(0,5,10,15), intArrayOf(3,6,9,12) // diagonals
        )
        
        for (line in winLines) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 3 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                if (board.get(emptyPos) == null) return emptyPos
            }
        }
        
        return null
    }
    
    private fun boardToString(board: Game.TicTacToe4x4Board): String {
        return (0..15).joinToString("") { pos ->
            when (board.get(pos)) {
                Game.Player.HUMAN -> "X"
                Game.Player.BOT -> "O"
                else -> "."
            }
        }
    }
}
