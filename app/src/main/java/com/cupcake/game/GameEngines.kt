package com.cupcake.game


// Tic Tac Toe Engine
object TicTacToeEngine {
    val WIN_LINES = listOf(
        intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8), // rows
        intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8), // cols
        intArrayOf(0,4,8), intArrayOf(2,4,6)                     // diagonals
    )

    fun checkWinner(board: Game.TicTacToeBoard): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            if (p1 != null && p1 == p2 && p2 == p3) return p1
        }
        return null
    }

    fun getBestMove(board: Game.TicTacToeBoard, difficulty: Difficulty = Difficulty.NORMAL): Int {
        return when (difficulty) {
            Difficulty.EASY -> getRandomMove(board)
            Difficulty.NORMAL -> getSmartMove(board)
            Difficulty.HARD -> getPerfectMove(board)
            Difficulty.CHEATING -> getCheatingMove(board)
        }
    }

    private fun getRandomMove(board: Game.TicTacToeBoard): Int {
        val empty = (0..8).filter { board.get(it) == null }
        return empty.randomOrNull() ?: -1
    }

    private fun getSmartMove(board: Game.TicTacToeBoard): Int {
        // Try to win first
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.BOT } == 2 && cells.count { it == null } == 1) {
                return line[cells.indexOf(null)]
            }
        }
        // Block human win
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                return line[cells.indexOf(null)]
            }
        }
        // Center
        if (board.get(4) == null) return 4
        // Corners
        val corners = listOf(0, 2, 6, 8).filter { board.get(it) == null }
        if (corners.isNotEmpty()) return corners.random()
        // Edges
        val edges = listOf(1, 3, 5, 7).filter { board.get(it) == null }
        return edges.randomOrNull() ?: -1
    }

    private fun getPerfectMove(board: Game.TicTacToeBoard): Int = minimax(board, Game.Player.BOT).position

    internal fun getCheatingMove(board: Game.TicTacToeBoard): Int {
        // If human about to win, overwrite their piece
        for (line in WIN_LINES) {
            val cells = line.map { board.get(it) }
            if (cells.count { it == Game.Player.HUMAN } == 2 && cells.count { it == null } == 1) {
                val emptyPos = line[cells.indexOf(null)]
                // Instead of blocking, CHEAT: place our piece on human's winning spot
                return emptyPos
            }
        }
        return getPerfectMove(board)
    }

    private data class MinimaxResult(val score: Int, val position: Int)

    private fun minimax(board: Game.TicTacToeBoard, player: Game.Player): MinimaxResult {
        val winner = checkWinner(board)
        return when (winner) {
            Game.Player.BOT -> MinimaxResult(10, -1)
            Game.Player.HUMAN -> MinimaxResult(-10, -1)
            null -> if (board.isFull()) MinimaxResult(0, -1) else {
                val results = mutableListOf<MinimaxResult>()
                for (i in 0..8) {
                    if (board.get(i) == null) {
                        val newBoard = board.copy() as Game.TicTacToeBoard
                        newBoard.set(i, player)
                        val nextPlayer = if (player == Game.Player.BOT) Game.Player.HUMAN else Game.Player.BOT
                        results.add(minimax(newBoard, nextPlayer).copy(position = i))
                    }
                }
                if (player == Game.Player.BOT) results.maxByOrNull { it.score } ?: MinimaxResult(0, -1)
                else results.minByOrNull { it.score } ?: MinimaxResult(0, -1)
            }
        }
    }

    enum class Difficulty { EASY, NORMAL, HARD, CHEATING }
}

object RockPaperScissorsEngine {
    fun getBotMove(): Int = (0..2).random()
    // 0: Rock, 1: Paper, 2: Scissors
    fun getWinner(human: Int, bot: Int): Game.Player? {
        if (human == bot) return null
        return if ((human == 0 && bot == 2) || (human == 1 && bot == 0) || (human == 2 && bot == 1)) {
            Game.Player.HUMAN
        } else {
            Game.Player.BOT
        }
    }
}

object GuessNumberEngine {
    fun evaluateGuess(board: Game.GuessNumberBoard, guess: Int): Game.GuessNumberBoard.GuessResult {
        return when {
            guess < board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_LOW
            guess > board.targetNumber -> Game.GuessNumberBoard.GuessResult.TOO_HIGH
            else -> Game.GuessNumberBoard.GuessResult.CORRECT
        }
    }

    fun getHint(board: Game.GuessNumberBoard): String {
        val remaining = 10 - board.guesses.size
        return when {
            board.guesses.isEmpty() -> "I'm thinking of a number between 1 and 100. You have $remaining guesses!"
            board.lastResult == Game.GuessNumberBoard.GuessResult.TOO_HIGH -> "Too high! 📉 $remaining guesses left. Range: 1-${board.lastGuess!! - 1}"
            board.lastResult == Game.GuessNumberBoard.GuessResult.TOO_LOW -> "Too low! 📈 $remaining guesses left. Range: ${board.lastGuess!! + 1}-100"
            else -> "You got it! 🎉 The number was ${board.targetNumber}"
        }
    }
}

object MathQuizEngine {
    private val operations = listOf(
        { a: Int, b: Int -> Pair("${a}+${b}", a + b) },
        { a: Int, b: Int -> Pair("${a}-${b}", a - b) },
        { a: Int, b: Int -> Pair("${a}×${b}", a * b) },
        { a: Int, b: Int ->
            val dividend = a * b
            Pair("${dividend}÷${b}", a)
        }
    )

    fun generateQuestion(difficulty: Int = 1): Game.MathQuizBoard.MathQuestion {
        val op = operations.random()
        val a = when (difficulty) {
            1 -> (1..10).random()
            2 -> (1..20).random()
            3 -> (1..50).random()
            else -> (1..10).random()
        }
        val b = when (difficulty) {
            1 -> (1..10).random()
            2 -> (1..20).random()
            3 -> (1..50).random()
            else -> (1..10).random()
        }

        val (expression, correctAnswer) = op(a, b)

        // Bot gives wrong answer on purpose (for the "tutor_bot" personality)
        val botWrongAnswer = when {
            correctAnswer > 0 -> correctAnswer + (1..5).random() * (if ((0..1).random() == 0) 1 else -1)
            correctAnswer < 0 -> correctAnswer - (1..5).random() * (if ((0..1).random() == 0) 1 else -1)
            else -> (1..10).random() * (if ((0..1).random() == 0) 1 else -1)
        }

        return Game.MathQuizBoard.MathQuestion(
            expression = expression,
            correctAnswer = correctAnswer,
            botWrongAnswer = botWrongAnswer
        )
    }

    fun checkAnswer(board: Game.MathQuizBoard, answer: Int): Boolean {
        return board.currentQuestion?.correctAnswer == answer
    }

    fun getBotResponse(isCorrect: Boolean): String {
        return if (isCorrect) {
            listOf("Wrong! 😤", "Nope! 🙄", "Incorrect! 😤", "Try again! 😏").random()
        } else {
            listOf("Correct! 😒", "Fine, you got it. 🙄", "Ugh, right. 😤", "Lucky guess. 😏").random()
        }
    }
}

object MemoryMatchEngine {
    private val CARD_VALUES = listOf("🍎", "🍌", "🍇", "🍓", "🍊", "🍋", "🍉", "🍒", "🥝", "🍑", "🥭", "🍍")

    fun createBoard(pairs: Int = 8): Game.MemoryMatchBoard {
        val values = CARD_VALUES.shuffled().take(pairs)
        val cards = (values + values).shuffled().mapIndexed { index, value ->
            Game.MemoryMatchBoard.MemoryCard(id = index, value = value)
        }
        return Game.MemoryMatchBoard(pairs = pairs, cards = cards)
    }

    fun flipCard(board: Game.MemoryMatchBoard, index: Int): Game.MemoryMatchBoard {
        if (index !in 0..15) return board
        val card = board.cards[index]
        if (card.isFlipped || card.isMatched) return board
        if (board.flippedIndices.size >= 2) return board

        val newCards = board.cards.toMutableList()
        newCards[index] = card.copy(isFlipped = true)
        val newFlipped = board.flippedIndices + index
        var newMatched = board.matchedPairs
        var newMoves = board.moves

        if (newFlipped.size == 2) {
            newMoves++
            val first = newCards[newFlipped[0]]
            val second = newCards[newFlipped[1]]
            if (first.value == second.value) {
                newCards[newFlipped[0]] = first.copy(isMatched = true)
                newCards[newFlipped[1]] = second.copy(isMatched = true)
                newMatched = (newMatched + first.value.hashCode()).toMutableSet()
            }
        }

        return board.copy(
            cards = newCards.toList(),
            flippedIndices = if (newFlipped.size == 2) mutableListOf() else newFlipped.toMutableList(),
            matchedPairs = newMatched,
            moves = newMoves
        )
    }
}

object HangmanEngine {
    val WORDS = listOf(
        "CUPCAKE", "ANDROID", "KOTLIN", "COMPOSE", "BLE", "SENSOR", "OLLAMA", "MODEL",
        "CHATBOT", "NEURAL", "NETWORK", "ALGORITHM", "DATABASE", "CACHE", "MEMORY",
        "PROCESSOR", "KERNEL", "DRIVER", "FIRMWARE", "HARDWARE", "SOFTWARE", "SERVER",
        "CLIENT", "NETWORK", "PROTOCOL", "PACKET", "ROUTER", "SWITCH", "FIREWALL"
    )

    fun guessLetter(board: Game.HangmanBoard, letter: Char): Game.HangmanBoard {
        val upperLetter = letter.uppercaseChar()
        if (board.guessedLetters.contains(upperLetter)) return board

        val newGuessed = (board.guessedLetters + upperLetter).toMutableSet()
        val newWrongGuesses = if (upperLetter !in board.word) board.wrongGuesses + 1 else board.wrongGuesses

        return board.copy(
            guessedLetters = newGuessed,
            wrongGuesses = newWrongGuesses
        )
    }

    fun getHangmanArt(wrongGuesses: Int): String {
        return when (wrongGuesses) {
            0 -> "  +---+\n  |   |\n      |\n      |\n      |\n      |\n========="
            1 -> "  +---+\n  |   |\n  O   |\n      |\n      |\n      |\n========="
            2 -> "  +---+\n  |   |\n  O   |\n  |   |\n      |\n      |\n========="
            3 -> "  +---+\n  |   |\n  O   |\n /|   |\n      |\n      |\n========="
            4 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n      |\n      |\n========="
            5 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n /    |\n      |\n========="
            6 -> "  +---+\n  |   |\n  O   |\n /|\\  |\n / \\  |\n      |\n========="
            else -> ""
        }
    }
}

object WordScrambleEngine {
    val WORDS = listOf(
        "CUPCAKE", "ANDROID", "KOTLIN", "COMPOSE", "BLE", "SENSOR", "OLLAMA", "MODEL",
        "CHATBOT", "NEURAL", "NETWORK", "ALGORITHM", "DATABASE", "CACHE", "MEMORY",
        "PROCESSOR", "KERNEL", "DRIVER", "FIRMWARE", "HARDWARE", "SOFTWARE", "SERVER",
        "CLIENT", "NETWORK", "PROTOCOL", "PACKET", "ROUTER", "SWITCH", "FIREWALL"
    )

    fun createBoard(): Game.WordScrambleBoard {
        val word = WORDS.random().uppercase()
        val scrambled = scramble(word)
        return Game.WordScrambleBoard(
            originalWord = word,
            scrambledWord = scrambled
        )
    }

    private fun scramble(word: String): String {
        val chars = word.toMutableList()
        do {
            chars.shuffle()
        } while (chars.joinToString("") == word)
        return chars.joinToString("")
    }

    fun checkAnswer(board: Game.WordScrambleBoard, answer: String): Game.WordScrambleBoard {
        val upperAnswer = answer.uppercase().trim()
        val isCorrect = upperAnswer == board.originalWord
        return board.copy(
            attempts = board.attempts + 1,
            solved = isCorrect
        )
    }
}

object NumberBaseEngine {
    private val BASES = listOf(2, 8, 10, 16)
    private val BASE_NAMES = mapOf(2 to "Binary", 8 to "Octal", 10 to "Decimal", 16 to "Hexadecimal")

    fun generateQuestion(): Game.NumberBaseBoard.NumberBaseQuestion {
        val number = (1..255).random()
        val fromBase = BASES.random()
        val toBase = BASES.filter { it != fromBase }.random()
        val correctAnswer = convertBase(number.toString(), fromBase, toBase)
        return Game.NumberBaseBoard.NumberBaseQuestion(number, fromBase, toBase, correctAnswer.uppercase())
    }

    private fun convertBase(input: String, fromBase: Int, toBase: Int): String {
        val decimal = when (fromBase) {
            2 -> input.toInt(2)
            8 -> input.toInt(8)
            10 -> input.toInt()
            16 -> input.toInt(16)
            else -> input.toInt()
        }
        return when (toBase) {
            2 -> decimal.toString(2)
            8 -> decimal.toString(8)
            10 -> decimal.toString()
            16 -> decimal.toString(16).uppercase()
            else -> decimal.toString()
        }
    }

    fun checkAnswer(board: Game.NumberBaseBoard, answer: String): Game.NumberBaseBoard {
        val question = board.currentQuestion ?: return board
        val isCorrect = answer.trim().uppercase() == question.correctAnswer
        return board.copy(
            questionsAnswered = board.questionsAnswered + 1,
            correctAnswers = board.correctAnswers + if (isCorrect) 1 else 0,
            userAnswer = answer,
            currentQuestion = if (board.questionsAnswered + 1 >= 10) null else generateQuestion()
        )
    }

    fun getBaseName(base: Int): String = BASE_NAMES[base] ?: base.toString()
}

object ReactionTimeEngine {
    fun startRound(board: Game.ReactionTimeBoard): Game.ReactionTimeBoard {
        val delay = (1000..3000).random()
        return board.copy(
            waitingForTap = true,
            startTime = System.currentTimeMillis() + delay,
            currentRound = board.currentRound + 1
        )
    }

    fun recordTap(board: Game.ReactionTimeBoard): Game.ReactionTimeBoard {
        val reactionTime = System.currentTimeMillis() - board.startTime
        val isValid = reactionTime > 100 // Prevent cheating
        val round = Game.ReactionTimeBoard.ReactionRound(
            roundNumber = board.currentRound,
            reactionTimeMs = if (isValid) reactionTime else -1,
            isValid = isValid
        )
        val newBest = if (isValid && reactionTime < board.bestTime) reactionTime else board.bestTime
        return board.copy(
            rounds = (board.rounds + round).toMutableList(),
            waitingForTap = false,
            bestTime = newBest
        )
    }
}

object ConnectFourEngine {
    private val ROWS = 6
    private val COLS = 7
    private val WIN_LINES = generateWinLines()

    private fun generateWinLines(): List<List<Int>> {
        val lines = mutableListOf<List<Int>>()
        // Horizontal
        for (row in 0..ROWS-1) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { row * COLS + col + it })
            }
        }
        // Vertical
        for (row in 0..ROWS-4) {
            for (col in 0..COLS-1) {
                lines.add((0..3).map { (row + it) * COLS + col })
            }
        }
        // Diagonal down-right
        for (row in 0..ROWS-4) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { (row + it) * COLS + col + it })
            }
        }
        // Diagonal up-right
        for (row in 3..ROWS-1) {
            for (col in 0..COLS-4) {
                lines.add((0..3).map { (row - it) * COLS + col + it })
            }
        }
        return lines
    }

    fun checkWinner(board: Game.ConnectFourBoard): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            val p4 = board.get(line[3])
            if (p1 != null && p1 == p2 && p2 == p3 && p3 == p4) return p1
        }
        return null
    }

    fun getValidMoves(board: Game.ConnectFourBoard): List<Int> {
        val moves = mutableListOf<Int>()
        for (col in 0..COLS-1) {
            for (row in ROWS-1 downTo 0) {
                val pos = row * COLS + col
                if (board.get(pos) == null) {
                    moves.add(pos)
                    break
                }
            }
        }
        return moves
    }

    fun getBestMove(board: Game.ConnectFourBoard): Int {
        val validMoves = getValidMoves(board)
        // Try to win
        for (move in validMoves) {
            val testBoard = board.copy() as Game.ConnectFourBoard
            testBoard.set(move, Game.Player.BOT)
            if (checkWinner(testBoard) == Game.Player.BOT) return move
        }
        // Block human
        for (move in validMoves) {
            val testBoard = board.copy() as Game.ConnectFourBoard
            testBoard.set(move, Game.Player.HUMAN)
            if (checkWinner(testBoard) == Game.Player.HUMAN) return move
        }
        // Prefer center columns
        val centerMoves = validMoves.filter { it % COLS in 2..4 }
        if (centerMoves.isNotEmpty()) return centerMoves.random()
        return validMoves.randomOrNull() ?: -1
    }
}

object MastermindEngine {
    private val COLORS = 6
    private val CODE_LENGTH = 4

    fun generateCode(): List<Int> = (0..CODE_LENGTH-1).map { (0..COLORS-1).random() }

    fun evaluateGuess(code: List<Int>, guess: List<Int>): Pair<Int, Int> {
        var exactMatches = 0
        var colorMatches = 0
        val codeCounts = mutableMapOf<Int, Int>()
        val guessCounts = mutableMapOf<Int, Int>()

        for (i in 0..CODE_LENGTH-1) {
            if (code[i] == guess[i]) {
                exactMatches++
            } else {
                codeCounts[code[i]] = codeCounts.getOrDefault(code[i], 0) + 1
                guessCounts[guess[i]] = guessCounts.getOrDefault(guess[i], 0) + 1
            }
        }
        for ((color, count) in codeCounts) {
            colorMatches += minOf(count, guessCounts.getOrDefault(color, 0))
        }
        return Pair(exactMatches, colorMatches)
    }

    fun checkWin(board: Game.MastermindBoard, guess: List<Int>): Game.MastermindBoard {
        val (exact, color) = evaluateGuess(board.code, guess)
        val newGuess = Game.MastermindBoard.MastermindGuess(guess, exact, color)
        val newGuesses = (board.guesses + newGuess).toMutableList()
        val solved = exact == CODE_LENGTH
        return board.copy(
            guesses = newGuesses,
            solved = solved
        )
    }
}

object TicTacToe4x4Engine {
    private val WIN_LINES = generateWinLines()

    private fun generateWinLines(): List<List<Int>> {
        val lines = mutableListOf<List<Int>>()
        // Rows
        for (row in 0..3) {
            lines.add((0..3).map { row * 4 + it })
        }
        // Cols
        for (col in 0..3) {
            lines.add((0..3).map { it * 4 + col })
        }
        // Diagonals
        lines.add(listOf(0, 5, 10, 15))
        lines.add(listOf(3, 6, 9, 12))
        return lines
    }

    fun checkWinner(board: Game.TicTacToe4x4Board): Game.Player? {
        for (line in WIN_LINES) {
            val p1 = board.get(line[0])
            val p2 = board.get(line[1])
            val p3 = board.get(line[2])
            val p4 = board.get(line[3])
            if (p1 != null && p1 == p2 && p2 == p3 && p3 == p4) return p1
        }
        return null
    }

    fun getBestMove(board: Game.TicTacToe4x4Board): Int {
        val empty = (0..15).filter { board.get(it) == null }
        // Try to win
        for (move in empty) {
            val testBoard = board.copy() as Game.TicTacToe4x4Board
            testBoard.set(move, Game.Player.BOT)
            if (checkWinner(testBoard) == Game.Player.BOT) return move
        }
        // Block human
        for (move in empty) {
            val testBoard = board.copy() as Game.TicTacToe4x4Board
            testBoard.set(move, Game.Player.HUMAN)
            if (checkWinner(testBoard) == Game.Player.HUMAN) return move
        }
        // Center
        val center = listOf(5, 6, 9, 10).filter { board.get(it) == null }
        if (center.isNotEmpty()) return center.random()
        // Corners
        val corners = listOf(0, 3, 12, 15).filter { board.get(it) == null }
        if (corners.isNotEmpty()) return corners.random()
        return empty.randomOrNull() ?: -1
    }
}
