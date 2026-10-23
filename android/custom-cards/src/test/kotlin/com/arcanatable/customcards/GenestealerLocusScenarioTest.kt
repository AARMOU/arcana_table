package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GenestealerLocus
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class GenestealerLocusScenarioTest {
    @Test
    fun everyCreatureAttackingItsControllerGetsMinusOnePowerUntilEndOfTurn() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val p = d.player1
        val q = d.player2
        d.putCreatureOnBattlefield(q, "Genestealer Locus")
        val bears = List(2) { d.putCreatureOnBattlefield(p, "Grizzly Bears") }
        bears.forEach { d.removeSummoningSickness(it) }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, bears, q).error)
        d.resolveAll()
        bears.forEach {
            assertEquals(1, d.projected.getPower(it))
            assertEquals(2, d.projected.getToughness(it))
        }
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = q)
        bears.forEach { assertEquals(2, d.projected.getPower(it)) }
    }

    @Test
    fun creaturesAttackingAnOpponentGetToughnessEvenWhenControlledByLocusController() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Genestealer Locus")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(2, d.projected.getToughness(bear))
    }

    @Test
    fun attackingAPlaneswalkerDoesNotQualifyForEitherTrigger() {
        for (locusOnDefendingSide in listOf(true, false)) {
            val d = customScenario(GenestealerLocus, "Grizzly Bears", "Jace Beleren")
            val p = d.player1
            val q = d.player2
            d.putCreatureOnBattlefield(if (locusOnDefendingSide) q else p, "Genestealer Locus")
            val walker = d.putPermanentOnBattlefield(q, "Jace Beleren")
            d.addComponent(walker, CountersComponent(mapOf(CounterType.LOYALTY to 3)))
            val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
            d.removeSummoningSickness(bear)
            d.passPriorityUntil(Step.DECLARE_ATTACKERS)
            assertNull(d.declareAttackers(p, listOf(bear), walker).error)
            d.resolveAll()
            assertEquals(2, d.projected.getPower(bear))
            assertEquals(2, d.projected.getToughness(bear))
        }
    }

    @Test
    fun anOpponentsCreatureAttackingAnotherOpponentAlsoGetsToughness() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val players = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        val controller = players[0]
        val attacker = players[1]
        val defender = players[2]
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = attacker)
        d.putCreatureOnBattlefield(controller, "Genestealer Locus")
        val bear = d.putCreatureOnBattlefield(attacker, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(attacker, listOf(bear), defender).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(3, d.projected.getToughness(bear))
    }

}
