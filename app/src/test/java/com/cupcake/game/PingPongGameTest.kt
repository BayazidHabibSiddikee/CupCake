package com.cupcake.game

import com.cupcake.ai.CharacterManager
import com.cupcake.chat.ChatSessionStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

/**
 * Ping Pong flows through the shared game plumbing: points update the score, the
 * match ends at the target, a human win makes CupCake angrier (raising adaptive
 * difficulty), and the reaction carries the right emotion for the UI/TTS.
 */
class PingPongGameTest {

    private lateinit var llmRouter: GameLLMRouter
    private lateinit var sessionStore: ChatSessionStore
    private lateinit var gameManager: GameManager

    @Before
    fun setUp() {
        llmRouter = mockk<GameLLMRouter>()
        sessionStore = ChatSessionStore()
        coEvery {
            llmRouter.generate(any(), any(), any(), any(), any())
        } coAnswers { "hehe! 😼" }
        gameManager = GameManager(llmRouter, CharacterManager, PointsManager(), sessionStore)
    }

    @Test
    fun `winning five points ends the match as a human win`() = runBlocking {
        gameManager.startGame(Game.GameType.PING_PONG)
        val reactionDeferred = async { withTimeout(5_000) { gameManager.gameReactions.first() } }
        delay(100)

        repeat(5) { gameManager.pingPongPoint(humanScored = true) }

        val reaction = reactionDeferred.await()
        assertThat(reaction.humanWon).isTrue()
        assertThat(reaction.mood).isEqualTo(GameManager.ReactionMood.ANGRY)

        val state = gameManager.getCurrentState()!!
        assertThat(state.status).isEqualTo(Game.GameStatus.HUMAN_WON)
        assertThat((state.board as Game.PingPongBoard).humanScore).isEqualTo(5)
    }

    @Test
    fun `human win raises anger and adaptive difficulty`() = runBlocking {
        gameManager.startGame(Game.GameType.PING_PONG)
        repeat(5) { gameManager.pingPongPoint(humanScored = true) }
        delay(50)

        assertThat(gameManager.getHumanWinStreak()).isEqualTo(1)
        assertThat(gameManager.getAngerLevel()).isGreaterThan(0.5)
        // Escalation promotes the base NORMAL difficulty for the next match.
        assertThat(gameManager.getAdaptiveDifficultyName()).isNotEqualTo("normal")
    }

    @Test
    fun `bot win is a gloating reaction and keeps her calm`() = runBlocking {
        gameManager.startGame(Game.GameType.PING_PONG)
        val reactionDeferred = async { withTimeout(5_000) { gameManager.gameReactions.first() } }
        delay(100)

        repeat(5) { gameManager.pingPongPoint(humanScored = false) }

        val reaction = reactionDeferred.await()
        assertThat(reaction.humanWon).isFalse()
        assertThat(reaction.mood).isEqualTo(GameManager.ReactionMood.GLOATING)
        assertThat(gameManager.getHumanWinStreak()).isEqualTo(0)
    }

    @Test
    fun `trash talk escalates with the win streak and stays clean`() {
        (1..6).forEach { streak ->
            val angry = BotTrashTalk.angryLine(streak)
            val sad = BotTrashTalk.sadLine(streak)
            assertThat(angry).isNotEmpty()
            assertThat(sad).isNotEmpty()
            // No hostile language: ToneGuard must pass these lines through untouched.
            assertThat(ToneGuard.sanitize(angry)).isEqualTo(angry)
            assertThat(ToneGuard.sanitize(sad)).isEqualTo(sad)
        }
        assertThat(BotTrashTalk.rankLabel(0.0)).isEqualTo("CALM")
        assertThat(BotTrashTalk.rankLabel(0.9)).isEqualTo("FURIOUS")
    }
}
