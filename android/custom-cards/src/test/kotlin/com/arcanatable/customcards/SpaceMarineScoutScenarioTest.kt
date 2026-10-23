package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpaceMarineScout
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class SpaceMarineScoutScenarioTest {
    @Test
    fun searchesForTappedPlainsWhenOpponentControlsMoreLands() {
        val d = customScenario(SpaceMarineScout)
        val p = d.player1
        val op = d.player2
        d.putPermanentOnBattlefield(op, "Plains")
        d.putPermanentOnBattlefield(op, "Plains")
        val scout = d.putCardInHand(p, "Space Marine Scout")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, scout).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Plains")))

        val newLand = d.permanentsNamed(p, "Plains").single()
        assertTrue(d.state.getEntity(newLand)!!.has<TappedComponent>())
        val creature = d.findPermanent(p, "Space Marine Scout")!!
        assertTrue(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(creature, Keyword.VIGILANCE))
    }

    @Test
    fun cannotSearchWhenNobodyHasMoreLands() {
        val d = customScenario(SpaceMarineScout)
        val p = d.player1
        val scout = d.putCardInHand(p, "Space Marine Scout")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, scout).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Plains").isEmpty())
    }
}
