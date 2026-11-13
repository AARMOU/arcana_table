package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SeaGateWreckage
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SeaGateWreckageIndependentCertificationTest {
    private fun GameTestDriver.emptyHand(player: EntityId) {
        getHand(player).forEach(::moveToGraveyard)
        assertEquals(0, getHandSize(player))
    }

    @Test
    fun tappingAddsOneColorlessManaAndCannotBeRepeatedWhileTapped() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).white)
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).green)
        assertTrue(d.isTapped(land))
        assertNotNull(d.activate(p, land, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun payingTwoAndOneColorlessWithAnEmptyHandDrawsExactlyOneCard() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")
        d.emptyHand(p)
        d.giveColorlessMana(p, 3)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size

        assertNull(d.activate(p, land, index = 1).error)
        assertTrue(d.isTapped(land))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
        d.resolveAll()

        assertEquals(1, d.getHandSize(p))
        assertEquals(libraryBefore - 1, d.zoneNames(p, Zone.LIBRARY).size)
        assertEquals(listOf("Plains"), d.zoneNames(p, Zone.HAND))
    }

    @Test
    fun theDrawAbilityCannotBeActivatedWithAnyCardInHand() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")
        d.giveColorlessMana(p, 3)
        val handBefore = d.getHandSize(p)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size

        assertTrue(handBefore > 0)
        assertNotNull(d.activate(p, land, index = 1).error)

        assertFalse(d.isTapped(land))
        assertEquals(3, d.manaPool(p).colorless)
        assertEquals(handBefore, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
    }

    @Test
    fun theDrawAbilityRequiresTheTwoGenericManaInAdditionToColorlessMana() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")
        d.emptyHand(p)
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.RED)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size

        assertNotNull(d.activate(p, land, index = 1).error)

        assertFalse(d.isTapped(land))
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(1, d.manaPool(p).red)
        assertEquals(0, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
    }

    @Test
    fun theColorlessSymbolCannotBePaidWithColoredMana() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")
        d.emptyHand(p)
        d.giveMana(p, Color.RED, 3)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size

        assertNotNull(d.activate(p, land, index = 1).error)

        assertFalse(d.isTapped(land))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(3, d.manaPool(p).red)
        assertEquals(0, d.getHandSize(p))
        assertEquals(libraryBefore, d.zoneNames(p, Zone.LIBRARY).size)
    }

    @Test
    fun theDrawAbilityCanBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val opponent = d.player2
        val land = d.putLandOnBattlefield(p, "Sea Gate Wreckage")
        d.emptyHand(p)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 3)
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY).size

        assertEquals(0, d.getHandSize(p))
        assertNull(d.activate(p, land, index = 1).error)
        assertTrue(d.isTapped(land))
        d.resolveAll()

        assertEquals(1, d.getHandSize(p))
        assertEquals(libraryBefore - 1, d.zoneNames(p, Zone.LIBRARY).size)
    }
}
