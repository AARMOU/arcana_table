package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnucklesTheEchidna
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KnucklesTheEchidnaIndependentCertificationTest {
    @Test
    fun castingRequiresTwoGenericAndTwoRedAndEntersAsATwoFourWithItsKeywords() {
        val d = customScenario(KnucklesTheEchidna)
        val player = d.player1
        val knuckles = d.putCardInHand(player, "Knuckles the Echidna")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, knuckles).error)
        d.giveMana(player, Color.RED, 2)
        assertNull(d.castSpell(player, knuckles).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(knuckles))
        assertEquals(2, d.projected.getPower(knuckles))
        assertEquals(4, d.projected.getToughness(knuckles))
        assertTrue(d.projected.hasColor(knuckles, Color.RED))
        assertTrue(d.projected.hasSubtype(knuckles, "ECHIDNA"))
        assertTrue(d.projected.hasSubtype(knuckles, "WARRIOR"))
        assertTrue(d.projected.hasKeyword(knuckles, Keyword.DOUBLE_STRIKE))
        assertTrue(d.projected.hasKeyword(knuckles, Keyword.TRAMPLE))
        assertTrue(d.projected.hasKeyword(knuckles, Keyword.HASTE))
    }

    @Test
    fun twoRedAndOnlyOneGenericCannotPayTheFourManaCost() {
        val d = customScenario(KnucklesTheEchidna)
        val player = d.player1
        val knuckles = d.putCardInHand(player, "Knuckles the Echidna")
        d.giveMana(player, Color.RED, 2)
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, knuckles).error)

        assertTrue(d.getHand(player).contains(knuckles))
        assertFalse(d.getPermanents(player).contains(knuckles))
    }

    @Test
    fun hasteAndDoubleStrikeDamageThePlayerTwiceAndGroupOneTreasurePerDamageStep() {
        val d = customScenario(KnucklesTheEchidna, "Grizzly Bears")
        val player = d.player1
        val knuckles = d.putCardInHand(player, "Knuckles the Echidna")
        d.giveMana(player, Color.RED, 2)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, knuckles).error)
        d.resolveAll()

        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(knuckles, bear), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(14, d.getLifeTotal(d.player2))
        assertEquals(2, d.permanentsNamed(player, "Treasure").size)
    }

    @Test
    fun trampleDealsExcessDamagePastAOneToughnessBlocker() {
        val d = customScenario(KnucklesTheEchidna, "Llanowar Elves")
        val knuckles = d.putCreatureOnBattlefield(d.player1, "Knuckles the Echidna")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.removeSummoningSickness(knuckles)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(knuckles), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(knuckles))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(17, d.getLifeTotal(d.player2))
        assertFalse(d.getPermanents(d.player2).contains(blocker))
        assertEquals(2, d.permanentsNamed(d.player1, "Treasure").size)
    }

    @Test
    fun combatDamageOnlyToCreaturesDoesNotCreateTreasures() {
        val d = customScenario(KnucklesTheEchidna, "Colossal Dreadmaw")
        val knuckles = d.putCreatureOnBattlefield(d.player1, "Knuckles the Echidna")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        d.removeSummoningSickness(knuckles)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(knuckles), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(knuckles))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(20, d.getLifeTotal(d.player2))
        assertTrue(d.getPermanents(d.player2).contains(blocker))
        assertFalse(d.getPermanents(d.player1).contains(knuckles))
        assertTrue(d.permanentsNamed(d.player1, "Treasure").isEmpty())
    }

    @Test
    fun thirtyArtifactsAtTheBeginningOfYourUpkeepWinTheGame() {
        val d = customScenario(KnucklesTheEchidna, "Sol Ring")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Knuckles the Echidna")
        repeat(30) { d.putPermanentOnBattlefield(player, "Sol Ring") }
        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.resolveAll()

        d.assertGameOver(expectedWinner = player)
    }

    @Test
    fun twentyNineArtifactsAreInsufficientAndReachingThirtyAfterUpkeepWaitsUntilNextUpkeep() {
        val d = customScenario(KnucklesTheEchidna, "Sol Ring")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Knuckles the Echidna")
        repeat(29) { d.putPermanentOnBattlefield(player, "Sol Ring") }
        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.resolveAll()

        assertFalse(d.state.gameOver)
        d.putPermanentOnBattlefield(player, "Sol Ring")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)

        assertFalse(d.state.gameOver)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.resolveAll()

        d.assertGameOver(expectedWinner = player)
    }

    @Test
    fun artifactsControlledByAnOpponentDoNotMeetTheUpkeepCondition() {
        val d = customScenario(KnucklesTheEchidna, "Sol Ring")
        d.putCreatureOnBattlefield(d.player1, "Knuckles the Echidna")
        repeat(30) { d.putPermanentOnBattlefield(d.player2, "Sol Ring") }
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player1)
        d.resolveAll()

        assertFalse(d.state.gameOver)
        assertEquals(30, d.permanentsNamed(d.player2, "Sol Ring").size)
    }
}
