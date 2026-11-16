package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DarksteelMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DarksteelMonolithIndependentCertificationTest {
    @Test
    fun darksteelMonolithRequiresEightManaToCast() {
        val d = customScenario(DarksteelMonolith)
        val p = d.player1
        val monolith = d.putCardInHand(p, "Darksteel Monolith")
        d.giveColorlessMana(p, 7)

        assertNotNull(d.castSpell(p, monolith).error)
        assertEquals(7, d.manaPool(p).colorless)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Darksteel Monolith"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, monolith).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Darksteel Monolith").isNotEmpty())
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun darksteelMonolithIsIndestructible() {
        val d = customScenario(DarksteelMonolith, "Disenchant")
        val p = d.player1
        val monolith = d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val disenchant = d.putCardInHand(p, "Disenchant")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, disenchant, targets = listOf(monolith)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Darksteel Monolith").contains(monolith))
        assertTrue(d.getGraveyardCardNames(p).contains("Disenchant"))
        assertFalse(d.getGraveyardCardNames(p).contains("Darksteel Monolith"))
    }

    @Test
    fun colorlessSpellFromHandCanBeCastForNoMana() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val ring = d.putCardInHand(p, "Sol Ring")

        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun decliningTheAlternativeCostPaysTheSpellsManaCost() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, ring).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun alternativeCostCanBeUsedOnlyOnceEachTurn() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val firstRing = d.putCardInHand(p, "Sol Ring")

        assertNull(d.castSpell(p, firstRing).error)
        d.resolveAll()

        val secondRing = d.putCardInHand(p, "Sol Ring")
        assertNotNull(d.castSpell(p, secondRing).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)

        assertNull(d.castSpell(p, secondRing).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
    }

    @Test
    fun coloredSpellCannotUseTheAlternativeCost() {
        val d = customScenario(DarksteelMonolith, "Lightning Bolt")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val bolt = d.putCardInHand(p, "Lightning Bolt")

        assertNotNull(d.castSpell(p, bolt, targets = listOf(d.player2)).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Lightning Bolt"))
        assertTrue(d.permanentsNamed(p, "Darksteel Monolith").isNotEmpty())
    }

    @Test
    fun colorlessSpellNotCastFromHandCannotUseTheAlternativeCost() {
        val d = customScenario(DarksteelMonolith, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Darksteel Monolith")
        val ring = d.putCardInGraveyard(p, "Sol Ring")

        assertNotNull(d.castSpell(p, ring).error)

        assertTrue(d.getGraveyardCardNames(p).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
    }
}
