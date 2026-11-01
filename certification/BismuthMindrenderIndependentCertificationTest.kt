package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BismuthMindrender
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class BismuthMindrenderIndependentCertificationTest {
    private val name = "Bismuth Mindrender"

    @Test
    fun castingRequiresThreeGenericAndBlackAndEntersColorlessAsAFourThreeWithMenace() {
        val d = customScenario(BismuthMindrender)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertEquals(4, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertTrue(d.projected.getColors(card).isEmpty())
        assertTrue(d.projected.hasKeyword(card, Keyword.MENACE))
    }

    @Test
    fun menaceCannotBeBlockedByOneCreatureAndTwoBlockersPreventTheDamageTrigger() {
        val d = customScenario(BismuthMindrender, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val mindrender = d.putCreatureOnBattlefield(p, name)
        val firstBlocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val secondBlocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(opponent, "Grizzly Bears")
        d.removeSummoningSickness(mindrender)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(mindrender), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(opponent, mapOf(firstBlocker to listOf(mindrender))).error)
        assertNull(
            d.declareBlockers(
                opponent,
                mapOf(firstBlocker to listOf(mindrender), secondBlocker to listOf(mindrender)),
            ).error,
        )
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opponent))
        assertTrue(d.zoneNames(opponent, Zone.LIBRARY).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Grizzly Bears"))
    }

    @Test
    fun combatDamageExilesLandsThroughTheFirstNonlandAndCastsItForItsManaValueInLife() {
        val d = customScenario(BismuthMindrender, "Forest", "Plains", "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val mindrender = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(opponent, "Sol Ring")
        d.putCardOnTopOfLibrary(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(opponent, "Forest")
        d.putCardOnTopOfLibrary(opponent, "Plains")
        d.removeSummoningSickness(mindrender)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(mindrender), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, emptyMap()).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertEquals(16, d.getLifeTotal(opponent))
        assertEquals(18, d.getLifeTotal(p))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Plains", "Forest")))
        assertTrue(d.zoneNames(p, Zone.EXILE).isEmpty())
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(opponent, Zone.LIBRARY).contains("Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun decliningTheCastLeavesTheFirstNonlandExiledWithoutPayingLife() {
        val d = customScenario(BismuthMindrender, "Forest", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val mindrender = d.putCreatureOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(opponent, "Grizzly Bears")
        d.putCardOnTopOfLibrary(opponent, "Forest")
        d.removeSummoningSickness(mindrender)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(mindrender), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, emptyMap()).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))

        assertEquals(16, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Forest", "Grizzly Bears")))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertFalse(d.zoneNames(opponent, Zone.LIBRARY).contains("Grizzly Bears"))
    }
}
