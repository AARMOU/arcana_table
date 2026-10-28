package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HormagauntHorde
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class HormagauntHordeScenarioTest {
    @Test
    fun ravenousXFiveAddsCountersAndDrawsCard() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val initialHandSize = d.zoneNames(p, Zone.HAND).size
        val horde = d.putCardInHand(p, "Hormagaunt Horde")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 6)

        assertNull(d.castXSpell(p, horde, 5).error)
        d.resolveAll()

        assertEquals(5, d.projected.getPower(horde))
        assertEquals(5, d.projected.getToughness(horde))
        assertEquals(initialHandSize + 1, d.zoneNames(p, Zone.HAND).size)
    }

    @Test
    fun ravenousBelowFiveAddsCountersWithoutDrawingCard() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val initialHandSize = d.zoneNames(p, Zone.HAND).size
        val horde = d.putCardInHand(p, "Hormagaunt Horde")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)

        assertNull(d.castXSpell(p, horde, 4).error)
        d.resolveAll()

        assertEquals(4, d.projected.getPower(horde))
        assertEquals(4, d.projected.getToughness(horde))
        assertEquals(initialHandSize, d.zoneNames(p, Zone.HAND).size)
    }

    @Test
    fun endlessSwarmPaysToReturnFromGraveyard() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val horde = d.putCardInGraveyard(p, "Hormagaunt Horde")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        val forest = d.putCardInHand(p, "Forest")

        assertNull(d.playLand(p, forest).error)
        d.resolveAll()

        assertTrue(horde in d.getHand(p))
        assertFalse(horde in d.getGraveyard(p))
    }

    @Test
    fun endlessSwarmMayBeDeclined() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val horde = d.putCardInGraveyard(p, "Hormagaunt Horde")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 2)
        val forest = d.putCardInHand(p, "Forest")

        assertNull(d.playLand(p, forest).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(horde in d.getGraveyard(p))
        assertFalse(horde in d.getHand(p))
    }

    @Test
    fun endlessSwarmDoesNotTriggerForAnOpponentsLand() {
        val d = customScenario(HormagauntHorde, "Forest")
        val p = d.player1
        val opponent = d.player2
        val horde = d.putCardInGraveyard(p, "Hormagaunt Horde")
        val forest = d.putCardInHand(opponent, "Forest")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        assertNull(d.playLand(opponent, forest).error)

        assertTrue(d.state.stack.isEmpty())
        assertTrue(horde in d.getGraveyard(p))
    }
}
