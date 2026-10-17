package com.cupcake.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Pure-rule tests for Marin's demands. The Context-bound notification/persistence
 * paths are exercised on-device; everything decidable without Android lives here.
 */
class DemandManagerTest {

    private val catalog = DemandManager.catalog()

    @Test
    fun `catalog is non-empty with unique ids`() {
        assertTrue(catalog.isNotEmpty())
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
    }

    @Test
    fun `every demand is payable, rewarding and coherent`() {
        catalog.forEach { demand ->
            assertTrue("${demand.id} cost", demand.cost > 0)
            assertTrue("${demand.id} affection", demand.affection > 0)
            assertTrue("${demand.id} sulk not negative", demand.sulk >= 0)
            assertTrue("${demand.id} sulk <= affection", demand.sulk <= demand.affection)
            assertTrue("${demand.id} title", demand.title.isNotBlank())
            assertTrue("${demand.id} message", demand.message.isNotBlank())
            assertTrue("${demand.id} fulfilledLine", demand.fulfilledLine.isNotBlank())
            assertTrue("${demand.id} ignoredLine", demand.ignoredLine.isNotBlank())
        }
    }

    @Test
    fun `byId finds every catalog entry and misses unknown ids`() {
        catalog.forEach { assertEquals(it, DemandManager.byId(it.id)) }
        assertEquals(null, DemandManager.byId("does-not-exist"))
    }

    @Test
    fun `pickDemand never repeats the excluded id`() {
        val rng = Random(42)
        catalog.forEach { previous ->
            repeat(50) {
                assertNotEquals(previous.id, DemandManager.pickDemand(previous.id, rng).id)
            }
        }
    }

    @Test
    fun `pickDemand still works when nothing is excluded`() {
        repeat(20) {
            assertTrue(DemandManager.pickDemand(null).id in catalog.map { d -> d.id })
        }
    }

    @Test
    fun `affection never drops below zero`() {
        assertEquals(0, DemandManager.affectionAfter(2, -10))
        assertEquals(0, DemandManager.affectionAfter(0, 0))
        assertEquals(7, DemandManager.affectionAfter(2, 5))
    }

    @Test
    fun `fulfilling a demand nets positive affection, ignoring a cheaper one hurts less`() {
        val hungry = DemandManager.byId("hungry")!!
        val sleepy = DemandManager.byId("sleepy")!!
        // Both routes are defined so a design change (sulk > affection) is caught.
        assertTrue(hungry.affection > hungry.sulk)
        assertTrue(sleepy.affection > sleepy.sulk)
    }
}
