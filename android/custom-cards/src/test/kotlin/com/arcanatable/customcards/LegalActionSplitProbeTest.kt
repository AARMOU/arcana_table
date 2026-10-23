package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.view.LegalActionEnricher
import org.junit.Assert.*
import org.junit.Test

/**
 * Test-only Build 123 profiling. No gameplay / engine changes.
 *
 * Measures enumeration and enrichment independently using the exact pinned runtime jars.
 */
class LegalActionSplitProbeTest {
    @Test(timeout = 240000)
    fun splitFullEnumerationActionsOnlyAndEnrichmentOnTokenBoard() {
        val tokens = System.getenv("PROBE_TOKENS")?.toIntOrNull() ?: 40
        val tokenName = System.getenv("PROBE_TOKEN_NAME") ?: "Food"
        require(tokens in 1..600)
        require(tokenName in setOf("Food", "Clue", "Treasure", "Grizzly Bears"))

        val d = scenario()
        repeat(tokens) { d.putToken(d.player1, tokenName) }
        d.giveColorlessMana(d.player1, 10)
        val state = d.state
        val player = d.player1
        val enumerator = LegalActionEnumerator(
            d.cardRegistry,
            d.services.manaSolver,
            d.services.costCalculator,
            d.services.predicateEvaluator,
            d.services.conditionEvaluator,
            d.services.turnManager
        )
        val enricher = LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
        fun <T> timed(label: String, block: () -> T): T {
            val t0 = System.nanoTime()
            val result = block()
            println("PROBE name=$tokenName tokens=$tokens phase=$label elapsedMs=${(System.nanoTime()-t0)/1_000_000.0}")
            return result
        }
        fun signature(actions: List<LegalAction>) = actions.map { a ->
            listOf(
                a.actionType,
                a.action.toString(),
                a.affordable.toString(),
                a.requiresTargets.toString(),
                a.validTargets?.joinToString(",") { it.value } ?: "null",
                a.targetCount.toString()
            ).joinToString("|")
        }.sorted()

        val full = timed("enumerate_FULL") {
            enumerator.enumerate(state, player, EnumerationMode.FULL)
        }
        val actionsOnly = timed("enumerate_ACTIONS_ONLY") {
            enumerator.enumerate(state, player, EnumerationMode.ACTIONS_ONLY)
        }
        assertEquals("ACTIONS_ONLY must offer the same semantic action multiset", signature(full), signature(actionsOnly))
        val enriched = timed("enrich_full") { enricher.enrich(full, state, player) }
        val empty = timed("enrich_empty") { enricher.enrich(emptyList(), state, player) }
        assertTrue("Enrich(emptyList) must still return empty list", empty.isEmpty())
        assertEquals(full.size, enriched.size)
        println("PROBE_RESULT name=$tokenName tokens=$tokens battlefield=${state.getBattlefield().size} fullActions=${full.size} actionsOnly=${actionsOnly.size} enriched=${enriched.size} semanticParity=true")
    }
}
