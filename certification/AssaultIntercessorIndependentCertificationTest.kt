package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AssaultIntercessor
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AssaultIntercessorIndependentCertificationTest {
    private val name = "Assault Intercessor"

    @Test
    fun castingForOneGenericWhiteAndBlackEntersAsAThreeTwoAstartesWarriorWithFirstStrikeAndMenace() {
        val d = customScenario(AssaultIntercessor)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(2, d.projected.getToughness(card))
        assertTrue(d.projected.hasColor(card, Color.WHITE))
        assertTrue(d.projected.hasColor(card, Color.BLACK))
        assertTrue(d.projected.hasSubtype(card, "ASTARTES"))
        assertTrue(d.projected.hasSubtype(card, "WARRIOR"))
        assertTrue(d.projected.hasKeyword(card, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(card, Keyword.MENACE))
    }

    @Test
    fun genericManaCannotReplaceTheRequiredBlackMana() {
        val d = customScenario(AssaultIntercessor)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.WHITE)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertFalse(d.getPermanents(p).contains(card))
    }

    @Test
    fun firstStrikeKillsTheBlockerBeforeItDealsDamageAndChainswordTriggers() {
        val d = customScenario(AssaultIntercessor, "Grizzly Bears")
        val intercessor = d.putCreatureOnBattlefield(d.player1, name)
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(intercessor)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(intercessor), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(intercessor))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player1).contains(intercessor))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(d.player1))
    }

    @Test
    fun menaceCannotBeBlockedByOneCreatureButCanBeBlockedByTwo() {
        val d = customScenario(AssaultIntercessor, "Ornithopter")
        val intercessor = d.putCreatureOnBattlefield(d.player1, name)
        val firstBlocker = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val secondBlocker = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        d.removeSummoningSickness(intercessor)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(intercessor), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(firstBlocker to listOf(intercessor))).error)
        assertNull(
            d.declareBlockers(
                d.player2,
                mapOf(firstBlocker to listOf(intercessor), secondBlocker to listOf(intercessor)),
            ).error,
        )
    }

    @Test
    fun aCreatureThatAnOpponentControlsDyingCausesThatOpponentToLoseTwoLife() {
        val d = customScenario(AssaultIntercessor, "Grizzly Bears")
        val intercessor = d.putCreatureOnBattlefield(d.player1, name)
        val attacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(attacker))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player1).contains(intercessor))
        assertTrue(d.getGraveyard(d.player2).contains(blocker))
        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(d.player1))
    }

    @Test
    fun anOwnCreatureDyingDoesNotTriggerChainsword() {
        val d = customScenario(AssaultIntercessor, "Grizzly Bears", "Giant Spider")
        val intercessor = d.putCreatureOnBattlefield(d.player1, name)
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(spider to listOf(bear))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player1).contains(bear))
        assertTrue(d.getPermanents(d.player1).contains(intercessor))
        assertTrue(d.getPermanents(d.player2).contains(spider))
        assertEquals(20, d.getLifeTotal(d.player2))
    }
}
