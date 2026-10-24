package com.cupcake.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async

class PointsManagerTest {

    private fun manager(store: PointsStore = InMemoryPointsStore()) = PointsManager(store)

    @Test
    fun `starts at level 1 with zero progress and no negative xp`() {
        val manager = manager()
        assertEquals(1, manager.level.value)
        assertEquals(0f, manager.xpProgress.value, 0.0001f)
        val (current, needed) = manager.getLevelProgress()
        assertEquals(0L, current)
        assertTrue("level span must be positive", needed > 0L)
    }

    @Test
    fun `light progress never reports negative xp`() {
        val manager = manager()
        manager.awardPoints(15, Game.GameType.CLICKER, won = true)
        val (current, needed) = manager.getLevelProgress()
        assertTrue("current xp was $current", current >= 0L)
        assertTrue("level span was $needed", needed > 0L)
        assertTrue(manager.xpProgress.value in 0f..1f)
    }

    @Test
    fun `points and xp both grow and persist`() {
        val store = InMemoryPointsStore()
        val manager = manager(store)
        manager.awardPoints(100, Game.GameType.CHESS, won = true)

        assertTrue(manager.points.value > 0L)
        assertTrue(manager.xp.value > 0L)
        assertEquals(manager.points.value, manager.xp.value)
        assertEquals(manager.points.value, store.load().points)
        assertEquals(manager.xp.value, store.load().xp)
    }

    @Test
    fun `reload restores state from the store`() {
        val store = InMemoryPointsStore()
        val first = manager(store)
        first.awardPoints(100, Game.GameType.CHESS, won = true)
        first.awardPoints(30, Game.GameType.PING_PONG, won = false)
        val expectedPoints = first.points.value
        val expectedGames = first.totalGamesPlayed.value

        val reloaded = manager(store)
        assertEquals(expectedPoints, reloaded.points.value)
        assertEquals(expectedGames, reloaded.totalGamesPlayed.value)
        assertEquals(1, reloaded.gameStats.value[Game.GameType.CHESS]?.wins)
    }

    @Test
    fun `losing a game awards no coins and no xp`() {
        val manager = manager()
        manager.awardPoints(100, Game.GameType.CHESS, won = false)

        assertEquals(0L, manager.points.value)
        assertEquals(0L, manager.xp.value)
        assertEquals(1, manager.totalGamesPlayed.value)
        assertEquals(0, manager.totalWins.value)
        assertEquals(1, manager.gameStats.value[Game.GameType.CHESS]?.gamesPlayed)
        assertEquals(0, manager.gameStats.value[Game.GameType.CHESS]?.wins)
    }

    @Test
    fun `a win pays back more than the entry cost so the loop is sustainable`() {
        val manager = manager()
        manager.awardPoints(100, Game.GameType.CHESS, won = true)
        assertTrue(
            "win payout ${manager.points.value} should exceed entry cost ${PointsManager.GAME_ENTRY_COST}",
            manager.points.value > PointsManager.GAME_ENTRY_COST
        )
    }

    @Test
    fun `spendPoints is gated and never goes negative`() {        val manager = manager()
        manager.awardPoints(20, Game.GameType.CLICKER, won = true)
        val balance = manager.points.value
        assertTrue(balance > 0)

        assertFalse("spending more than the balance must fail", manager.spendPoints(balance + 1))
        assertEquals(balance, manager.points.value)

        assertTrue(manager.spendPoints(balance))
        assertEquals(0L, manager.points.value)
    }

    @Test
    fun `spending coins does not lower xp or level`() {
        val manager = manager()
        manager.awardPoints(100, Game.GameType.CHESS, won = true)
        val xpBefore = manager.xp.value
        val levelBefore = manager.level.value

        assertTrue(manager.spendPoints(10))
        assertEquals(xpBefore, manager.xp.value)
        assertEquals(levelBefore, manager.level.value)
    }

    @Test
    fun `level rises with xp and progress stays in range`() {
        val manager = manager()
        repeat(20) { manager.awardPoints(100, Game.GameType.CHESS, won = true) }
        assertTrue("should have levelled up", manager.level.value > 1)
        assertTrue(manager.xpProgress.value in 0f..1f)
    }

    @Test
    fun `level up events fire when a threshold is crossed`() = kotlinx.coroutines.runBlocking {
        val manager = manager()
        // Unconfined starts the collector immediately, so it is subscribed before we emit.
        val firstLevelUp = kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.Unconfined) {
            manager.levelUpEvents.first()
        }

        repeat(20) { manager.awardPoints(100, Game.GameType.CHESS, won = true) }

        val level = kotlinx.coroutines.withTimeout(2_000) { firstLevelUp.await() }
        assertTrue("expected level >= 2 but was $level", level >= 2)
    }
    @Test
    fun `awardPoints records total games, wins and per-game stats for win and loss`() {
        val manager = manager()
        manager.awardPoints(100, Game.GameType.CHESS, won = true)
        manager.awardPoints(40, Game.GameType.PING_PONG, won = false) // loss
        manager.awardPoints(30, Game.GameType.CONNECT_FOUR, won = false) // draw-like outcome

        assertEquals(3, manager.totalGamesPlayed.value)
        assertEquals(1, manager.totalWins.value)
        assertEquals(1, manager.gameStats.value[Game.GameType.CHESS]?.gamesPlayed)
        assertEquals(1, manager.gameStats.value[Game.GameType.CHESS]?.wins)
        assertEquals(1, manager.gameStats.value[Game.GameType.PING_PONG]?.gamesPlayed)
        assertEquals(0, manager.gameStats.value[Game.GameType.PING_PONG]?.wins)
        assertEquals(1, manager.gameStats.value[Game.GameType.CONNECT_FOUR]?.gamesPlayed)
        assertEquals(0, manager.gameStats.value[Game.GameType.CONNECT_FOUR]?.wins)
    }

    @Test
    fun `game stats and totals round-trip through the PointsStore`() {
        val store = InMemoryPointsStore()
        val first = manager(store)
        first.awardPoints(100, Game.GameType.CHESS, won = true)      // earned = 300
        first.awardPoints(40, Game.GameType.PING_PONG, won = false)   // earned = 0
        first.awardPoints(200, Game.GameType.WORD_SCRAMBLE, won = true) // earned = 300

        assertEquals(3, first.totalGamesPlayed.value)
        assertEquals(2, first.totalWins.value)

        // New manager over the same store must restore everything.
        val reloaded = manager(store)
        assertEquals(3, reloaded.totalGamesPlayed.value)
        assertEquals(2, reloaded.totalWins.value)

        val chess = reloaded.gameStats.value[Game.GameType.CHESS] ?: run { fail("CHESS stats missing"); return }
        assertEquals(1, chess.gamesPlayed)
        assertEquals(1, chess.wins)
        assertEquals(300L, chess.totalPoints)
        assertEquals(300, chess.bestScore)

        val pong = reloaded.gameStats.value[Game.GameType.PING_PONG] ?: run { fail("PING_PONG stats missing"); return }
        assertEquals(1, pong.gamesPlayed)
        assertEquals(0, pong.wins)
        assertEquals(0L, pong.totalPoints)

        val scramble = reloaded.gameStats.value[Game.GameType.WORD_SCRAMBLE] ?: run { fail("WORD_SCRAMBLE stats missing"); return }
        assertEquals(1, scramble.gamesPlayed)
        assertEquals(1, scramble.wins)
        assertEquals(300L, scramble.totalPoints)

        val snapshot = store.load()
        assertEquals(3, snapshot.totalGamesPlayed)
        assertEquals(3, snapshot.stats.size)
    }
}
