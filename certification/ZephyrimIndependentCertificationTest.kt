package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zephyrim
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.ChoiceSlot
import org.junit.Assert.*
import org.junit.Test

class ZephyrimIndependentCertificationTest {
    @Test
    fun normalCostRequiresThreeGenericAndOneWhiteAndEntersAsThreeThreeWithFlyingAndVigilance() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Zephyrim"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Zephyrim").contains(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(card, Keyword.VIGILANCE))
    }

    @Test
    fun squadPaysTwoForEachOfTwoCopiesWhenZephyrimEnters() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 7)

        // Squad is an additional casting cost, not a choice made while resolving.
        assertNull(
            d.submit(
                CastSpell(
                    playerId = p,
                    cardId = card,
                    declaredCostSlot = ChoiceSlot.SQUAD,
                    declaredCostTimes = 2,
                    paymentStrategy = PaymentStrategy.FromPool,
                ),
            ).error,
        )
        d.resolveAll()

        val copies = d.permanentsNamed(p, "Zephyrim")
        assertEquals(3, copies.size)
        assertTrue(copies.contains(card))
        copies.filter { it != card }.forEach { token ->
            assertEquals(3, d.projected.getPower(token))
            assertEquals(3, d.projected.getToughness(token))
            assertTrue(d.projected.hasKeyword(token, Keyword.FLYING))
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
        }
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun squadCanBeDeclinedAndCreatesNoCopies() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardInHand(p, "Zephyrim")
        d.giveMana(p, Color.WHITE)
        // Extra mana alone must not implicitly purchase Squad copies.
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertEquals(listOf(card), d.permanentsNamed(p, "Zephyrim"))
        assertEquals(4, d.manaPool(p).colorless)
    }

    @Test
    fun flyingPreventsGroundBlockersAndVigilanceLeavesZephyrimUntappedAfterAttacking() {
        val d = customScenario(Zephyrim, "Grizzly Bears")
        val p = d.player1
        val zephyrim = d.putCreatureOnBattlefield(p, "Zephyrim")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(zephyrim)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(zephyrim), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(zephyrim))).error)
        assertNull(d.declareBlockers(d.player2, emptyMap()).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(17, d.getLifeTotal(d.player2))
        assertFalse(d.isTapped(zephyrim))
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").contains(blocker))
    }

    @Test
    fun miracleCastsForOneWhiteAndOneGenericWhenZephyrimIsTheFirstCardDrawnThisTurn() {
        val d = customScenario(Zephyrim)
        val p = d.player1
        val card = d.putCardOnTopOfLibrary(p, "Zephyrim")
        d.passPriorityUntil(Step.DRAW, activePlayer = p)
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)
        d.resolveAll(Choices(yes = true))

        assertTrue(d.permanentsNamed(p, "Zephyrim").contains(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun miracleCannotCastZephyrimWhenItIsNotTheFirstCardDrawnThisTurn() {
        val d = customScenario(Zephyrim, "Divination", "Plains")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Zephyrim")
        d.putCardOnTopOfLibrary(p, "Plains")
        val divination = d.putCardInHand(p, "Divination")
        d.passPriorityUntil(Step.DRAW, activePlayer = p)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Plains"))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.WHITE)

        assertNull(d.castSpell(p, divination).error)
        d.resolveAll(Choices(yes = true))

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Zephyrim"))
        assertTrue(d.permanentsNamed(p, "Zephyrim").isEmpty())
    }
}
