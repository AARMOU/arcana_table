package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DesertOfTheGlorified
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
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
        assertTrue(d.abilityActions(p, land).isEmpty())

        d.untapPermanent(land)
        val abilities = d.abilityActions(p, land)
        assertEquals(1, abilities.size)
        assertTrue(abilities.single().action is ActivateAbility)
        assertNull(d.submit(abilities.single().action as ActivateAbility).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
        assertTrue(d.abilityActions(p, land).isEmpty())
    }

    @Test
    fun cyclingFromHandDuringAnOpponentsTurnDiscardsThisCardAndDrawsOne() {
        val d = customScenario(DesertOfTheGlorified, "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val land = d.putCardInHand(p, "Desert of the Glorified")
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)

        val abilities = d.abilityActions(p, land)
        assertEquals(1, abilities.size)
        assertTrue(abilities.single().action is ActivateAbility)
        assertNull(d.submit(abilities.single().action as ActivateAbility).error)

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Desert of the Glorified"))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Desert of the Glorified"))
        assertEquals(handBefore - 1, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
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

        assertTrue(d.abilityActions(p, land).isEmpty())
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

        assertTrue(d.abilityActions(p, land).isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Desert of the Glorified"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Desert of the Glorified"))
        assertEquals(handBefore, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(1, d.manaPool(p).colorless)
    }
}
