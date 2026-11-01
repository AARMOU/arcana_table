package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnucklesTheEchidna
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KnucklesTheEchidnaScenarioTest {
    @Test
    fun hasteDoubleStrikeTrampleAndBatchCombatDamageCreateATreasure() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears", "Wall of Omens")
        val p = d.player1
        val knuckles = d.putCardInHand(p, "Knuckles the Echidna")
        d.giveMana(p, Color.RED, 2)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, knuckles).error)
        d.resolveAll()
        d.addComponent(knuckles, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))

        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        val wall = d.putCreatureOnBattlefield(d.player2, "Wall of Omens")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(knuckles, bear), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(wall to listOf(knuckles))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(d.player2))
        assertEquals(1, d.permanentsNamed(p, "Treasure").size)
        assertTrue(d.getCreatures(d.player2).none { d.getCardName(it) == "Wall of Omens" })
    }

    @Test
    fun opponentCreaturesDealingCombatDamageDoNotCreateTreasures() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Knuckles the Echidna")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(bear)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(bear), d.player1).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = d.player2)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(d.player1))
        assertTrue(d.permanentsNamed(d.player1, "Treasure").isEmpty())
    }

    @Test
    fun upkeepWithThirtyArtifactsWinsTheGame() {
        val d = customScenario(KnucklesTheEchidna)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        repeat(30) { d.putToken(p, "Treasure") }

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        d.assertGameOver(p)
    }

    @Test
    fun upkeepWithFewerThanThirtyArtifactsDoesNotWinTheGame() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        repeat(29) { d.putToken(p, "Treasure") }
        d.putToken(p, "Grizzly Bears")

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertFalse(d.state.gameOver)
    }
}
