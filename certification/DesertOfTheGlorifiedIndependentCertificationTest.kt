package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DesertOfTheGlorified
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CycleCard
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DesertOfTheGlorifiedIndependentCertificationTest {
    @Test
    fun entersTappedAndItsTapAbilityAddsBlackAfterUntapping() {
        val d = customScenario(DesertOfTheGlorified)
        val p = d.player1
        val land = d.putCardInHand(p, "Desert of the Glorified")

        assertNull(d.playLand(p, land).error)
        d.resolveAll()
        assertTrue(d.isTapped(land))
        assertNotNull(d.activate(p, land).error)

        d.untapPermanent(land)
        val manaAbility = d.abilityActions(p, land).single().action as ActivateAbility
        assertNull(d.submit(manaAbility).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
        assertNotNull(d.activate(p, land).error)
    }

    @Test
    fun cyclingFromHandDuringAnOpponentsTurnDiscardsThisCardAndDrawsOne() {
        val d = customScenario(DesertOfTheGlorified, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val land = d.putCardInHand(p, "Desert of the Glorified")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        val cycleActions = d.legalActions(p).filter { (it.action as? CycleCard)?.cardId == land }
        assertEquals(1, cycleActions.size)
        assertTrue(cycleActions.single().affordable)
        assertNull(d.submit(cycleActions.single().action as CycleCard).error)

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Desert of the Glorified"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Desert of the Glorified"))
        assertEquals(handBefore - 1, d.getHandSize(p))
        assertEquals(libraryBefore - 1, d.zoneNames(p, Zone.LIBRARY).size)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
        d.resolveAll()

        assertEquals(handBefore, d.getHandSize(p))
        assertEquals(libraryBefore - 1, d.zoneNames(p, Zone.LIBRARY).size)
    }

    @Test
    fun cyclingRequiresGenericMana() {
        val d = customScenario(DesertOfTheGlorified, "Grizzly Bears")
        val p = d.player1
        val land = d.putCardInHand(p, "Desert of the Glorified")
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size
        d.giveMana(p, Color.BLACK)

        val cycleActions = d.legalActions(p).filter { (it.action as? CycleCard)?.cardId == land }
        assertEquals(1, cycleActions.size)
        assertFalse(cycleActions.single().affordable)
        assertNotNull(d.submit(cycleActions.single().action as CycleCard).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Desert of the Glorified"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Desert of the Glorified"))
        assertEquals(handBefore, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
        assertEquals(1, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun cyclingRequiresBlackMana() {
        val d = customScenario(DesertOfTheGlorified, "Grizzly Bears")
        val p = d.player1
        val land = d.putCardInHand(p, "Desert of the Glorified")
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size
        d.giveColorlessMana(p, 1)

        val cycleActions = d.legalActions(p).filter { (it.action as? CycleCard)?.cardId == land }
        assertEquals(1, cycleActions.size)
        assertFalse(cycleActions.single().affordable)
        assertNotNull(d.submit(cycleActions.single().action as CycleCard).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Desert of the Glorified"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Desert of the Glorified"))
        assertEquals(handBefore, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(1, d.manaPool(p).colorless)
    }
}
