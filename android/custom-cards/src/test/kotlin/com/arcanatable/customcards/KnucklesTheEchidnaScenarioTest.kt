package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnucklesTheEchidna
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class KnucklesTheEchidnaScenarioTest {
    @Test
    fun keywordAbilitiesWorkWhenKnucklesAttacksWhileSummoningSick() {
        val d = customScenario(KnucklesTheEchidna, "Memnite")
        val p = d.player1
        val knuckles = d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        val memnite = d.putCreatureOnBattlefield(d.player2, "Memnite")

        assertTrue(d.projected.hasKeyword(knuckles, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(knuckles, Keyword.TRAMPLE))
        assertTrue(d.projected.hasKeyword(knuckles, Keyword.HASTE))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull("Haste lets Knuckles attack without removing summoning sickness",
            d.declareAttackers(p, listOf(knuckles), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(memnite to listOf(knuckles))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals("trample and double strike deal one damage in the first-strike step and two later",
            17, d.getLifeTotal(d.player2))
        assertEquals(2, d.permanentsNamed(p, "Treasure").size)
    }

    @Test
    fun multipleCreaturesDealingDamageInOneStepCreateOnlyOneTreasure() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears")
        val p = d.player1
        val knuckles = d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(knuckles, bear), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(14, d.getLifeTotal(d.player2))
        assertEquals("one batch in each of the two combat-damage steps", 2,
            d.permanentsNamed(p, "Treasure").size)
    }

    @Test
    fun opponentCreaturesDealingCombatDamageDoNotCreateYourTreasure() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(bear)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(bear), p).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(p))
        assertTrue(d.permanentsNamed(p, "Treasure").isEmpty())
    }

    @Test
    fun treasureHunterDoesNotWinWithOnlyTwentyNineArtifacts() {
        val d = customScenario(KnucklesTheEchidna, "Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        repeat(29) { d.putPermanentOnBattlefield(p, "Sol Ring") }

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertFalse(d.state.gameOver)
    }

    @Test
    fun treasureHunterWinsAtTheBeginningOfUpkeepWithThirtyArtifacts() {
        val d = customScenario(KnucklesTheEchidna, "Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Knuckles the Echidna")
        repeat(30) { d.putPermanentOnBattlefield(p, "Sol Ring") }

        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()

        assertTrue(d.state.gameOver)
        assertEquals(p, d.state.winnerId)
    }
}
