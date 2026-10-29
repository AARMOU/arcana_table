package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Sporocyst
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SporocystScenarioTest {
    @Test
    fun ravenousXFiveDrawsAndSporeChimneyFindsUpToXBasicLandsTapped() {
        val d = customScenario(Sporocyst, "Forest", "Island", "Command Tower")
        val p = d.player1
        val sporocyst = d.putCardInHand(p, "Sporocyst")
        val initialHandSize = d.zoneNames(p, Zone.HAND).size
        d.putCardOnTopOfLibrary(p, "Command Tower")
        d.putCardOnTopOfLibrary(p, "Island")
        d.putCardOnTopOfLibrary(p, "Forest")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 10)

        assertNull(d.castXSpell(p, sporocyst, 5).error)
        d.resolveAll(Choices(pick = listOf("Forest", "Island")))

        assertEquals(5, d.projected.getPower(sporocyst))
        assertEquals(5, d.projected.getToughness(sporocyst))
        assertEquals(initialHandSize, d.zoneNames(p, Zone.HAND).size)
        assertNotNull(d.state.getEntity(d.findPermanent(p, "Forest")!!)!!.get<TappedComponent>())
        assertNotNull(d.state.getEntity(d.findPermanent(p, "Island")!!)!!.get<TappedComponent>())
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Command Tower"))
    }

    @Test
    fun ravenousBelowFiveDoesNotDrawAndSporeChimneyMayFindNoLands() {
        val d = customScenario(Sporocyst)
        val p = d.player1
        val sporocyst = d.putCardInHand(p, "Sporocyst")
        val initialHandSize = d.zoneNames(p, Zone.HAND).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 8)

        assertNull(d.castXSpell(p, sporocyst, 4).error)
        d.resolveAll(Choices(pick = listOf("no card")))

        assertEquals(4, d.projected.getPower(sporocyst))
        assertEquals(4, d.projected.getToughness(sporocyst))
        assertEquals(initialHandSize - 1, d.zoneNames(p, Zone.HAND).size)
        assertEquals(0, d.permanentsNamed(p, "Forest").size)
    }

    @Test
    fun defenderCannotAttack() {
        val d = customScenario(Sporocyst)
        val p = d.player1
        val sporocyst = d.putCardInHand(p, "Sporocyst")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)

        assertNull(d.castXSpell(p, sporocyst, 1).error)
        d.resolveAll()
        d.removeSummoningSickness(sporocyst)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNotNull(d.declareAttackers(p, listOf(sporocyst), d.player2).error)
    }
}
