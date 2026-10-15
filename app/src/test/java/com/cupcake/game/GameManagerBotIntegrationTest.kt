package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.chat.ChatSessionStore
import com.cupcake.data.model.ChatMessage
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

/**
 * Verifies the full move -> bot -> UI/chat pipeline:
 * every move is sent to the LLM AFTER it is applied, the bot's reply is
 * emitted on [GameManager.gameComments] / [GameManager.gameReactions],
 * and everything the bot says is persisted to the character's chat session.
 */
class GameManagerBotIntegrationTest {

    private lateinit var llmRouter: GameLLMRouter
    private lateinit var sessionStore: ChatSessionStore
    private lateinit var gameManager: GameManager

    @Before
    fun setUp() {
        llmRouter = mockk<GameLLMRouter>()
        sessionStore = ChatSessionStore()
        gameManager = GameManager(llmRouter, CharacterManager, PointsManager(), sessionStore)
    }

    /** Stub the LLM so every generate call immediately replies with [reply]. */
    private fun stubBotReply(reply: String, capturedPrompts: MutableList<String> = mutableListOf()) {
        val promptSlot = slot<String>()
        coEvery {
            llmRouter.generate(any(), any(), capture(promptSlot), any(), any())
        } coAnswers {
            capturedPrompts.add(promptSlot.captured)
            "$reply!"
        }
    }

    // ------------------------------------------
    // Moves are sent to the bot AFTER they are applied
    // ------------------------------------------

    @Test
    fun `human move is sent to the bot and the reply comes back as a comment`() = runBlocking {
        val prompts = mutableListOf<String>()
        stubBotReply("Well played", prompts)
        gameManager.startGame(Game.GameType.GUESS_NUMBER)

        val commentDeferred = async { withTimeout(5_000) { gameManager.gameComments.first() } }
        delay(100) // let the subscriber attach before the move

        gameManager.makeHumanMove(41) // guess 42

        val comment = commentDeferred.await()
        assertThat(comment).isEqualTo("Well played!")

        // The prompt must describe the move that was actually made...
        assertThat(prompts.first()).contains("Player: human guessed 42")
        // ...and include the UPDATED board (move applied before notifying)
        assertThat(prompts.first()).contains("guesses=[42]")
    }

    @Test
    fun `tic tac toe notifies the bot after its own reply move`() = runBlocking {
        stubBotReply("My turn is done")
        gameManager.startGame(Game.GameType.TIC_TAC_TOE)

        val commentDeferred = async { withTimeout(8_000) { gameManager.gameComments.first() } }
        delay(100)

        gameManager.makeHumanMove(4) // human takes center, bot answers after its delay

        val comment = commentDeferred.await()
        assertThat(comment).isEqualTo("My turn is done!")
    }

    @Test
    fun `text answers are sent to the bot with the typed answer`() = runBlocking {
        val prompts = mutableListOf<String>()
        stubBotReply("Nice answer", prompts)
        gameManager.startGame(Game.GameType.WORD_SCRAMBLE)
        val board = gameManager.getCurrentState()!!.board as Game.WordScrambleBoard

        val commentDeferred = async { withTimeout(5_000) { gameManager.gameComments.first() } }
        delay(100)

        gameManager.submitTextAnswer("ZZZZZZZZ") // wrong, game continues

        val comment = commentDeferred.await()
        assertThat(comment).isEqualTo("Nice answer!")
        assertThat(prompts.first()).contains("answered \"ZZZZZZZZ\"")
    }

    // ------------------------------------------
    // Everything the bot says lands in the chat session
    // ------------------------------------------

    @Test
    fun `bot move comments are persisted to the character chat session`() = runBlocking {
        stubBotReply("Interesting move")
        val characterId = CharacterManager.getCurrentCharacter()?.id ?: "marin_kitagawa"
        val before = sessionStore.messages(characterId).value.size

        gameManager.startGame(Game.GameType.GUESS_NUMBER)
        gameManager.makeHumanMove(10)

        withTimeout(5_000) {
            while (sessionStore.messages(characterId).value.size <= before) delay(50)
        }
        val messages = sessionStore.messages(characterId).value
        val last = messages.last()
        assertThat(last.content).isEqualTo("Interesting move!")
        assertThat(last.role).isEqualTo(ChatMessage.MessageRole.ASSISTANT)
    }

    @Test
    fun `game ending reaction is persisted to the character chat session`() = runBlocking {
        stubBotReply("Rematch right now")
        val characterId = CharacterManager.getCurrentCharacter()?.id ?: "marin_kitagawa"
        val before = sessionStore.messages(characterId).value.size

        gameManager.startGame(Game.GameType.WORD_SCRAMBLE)
        val board = gameManager.getCurrentState()!!.board as Game.WordScrambleBoard
        gameManager.submitTextAnswer(board.originalWord) // correct -> game over -> reaction

        withTimeout(5_000) {
            while (sessionStore.messages(characterId).value.size <= before) delay(50)
        }
        val messages = sessionStore.messages(characterId).value
        assertThat(messages.last().content).isEqualTo("Rematch right now!")
    }

    @Test
    fun `game ending reaction is emitted for the games screen bubble`() = runBlocking {
        stubBotReply("You got me")
        gameManager.startGame(Game.GameType.WORD_SCRAMBLE)
        val board = gameManager.getCurrentState()!!.board as Game.WordScrambleBoard

        val reactionDeferred = async { withTimeout(5_000) { gameManager.gameReactions.first() } }
        delay(100)

        gameManager.submitTextAnswer(board.originalWord)

        val reaction = reactionDeferred.await()
        assertThat(reaction.text).isEqualTo("You got me!")
        assertThat(reaction.humanWon).isTrue()
        assertThat(reaction.isDraw).isFalse()
    }

    // ------------------------------------------
    // Every game gets a humanized move description
    // ------------------------------------------

    @Test
    fun `every game type has a humanized move description`() {
        for (type in Game.GameType.entries) {
            val state = gameManager.startGame(type)
            val description = describeMove(type, "human", 3, state, detail = "42")

            assertThat(description).isNotEmpty()
            // The old generic fallback must be gone for every game
            assertThat(description).isNotEqualTo("made move 3")
        }
    }

    @Test
    fun `move descriptions are specific to each game`() {
        val ttt = gameManager.startGame(Game.GameType.TIC_TAC_TOE)
        assertThat(describeMove(Game.GameType.TIC_TAC_TOE, "bot", 4, ttt))
            .isEqualTo("placed O at cell 5")

        val hangman = gameManager.startGame(Game.GameType.HANGMAN)
        assertThat(describeMove(Game.GameType.HANGMAN, "human", 3, hangman))
            .isEqualTo("guessed the letter 'D'")

        val connect = gameManager.startGame(Game.GameType.CONNECT_FOUR)
        assertThat(describeMove(Game.GameType.CONNECT_FOUR, "human", 3, connect))
            .isEqualTo("dropped a disc in column 4")

        val memory = gameManager.startGame(Game.GameType.MEMORY_MATCH)
        assertThat(describeMove(Game.GameType.MEMORY_MATCH, "human", 7, memory))
            .isEqualTo("flipped card 8")

        val mastermind = gameManager.startGame(Game.GameType.MASTERMIND)
        assertThat(
            describeMove(
                Game.GameType.MASTERMIND, "human", 0, mastermind,
                detail = "🔴🔵🟢🟡 (exact=1, color=2)"
            )
        ).isEqualTo("guessed 🔴🔵🟢🟡 (exact=1, color=2)")
    }
}