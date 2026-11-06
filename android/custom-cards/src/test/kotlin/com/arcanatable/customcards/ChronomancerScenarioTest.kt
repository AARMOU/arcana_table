package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Chronomancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ChronomancerScenarioTest {
    @Test
    fun atomicTransmutationSacrificesAnotherArtifactToDraw() {
        val d = customScenario(Chronomancer, "Sol Ring")
        val p = d.player1
        val chronomancer = d.putCreatureOnBattlefield(p, "Chronomancer")
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")
        d.removeSummoningSickness(chronomancer)
        d.giveColorlessMana(p, 1)
        val handBefore = d.getHandSize(p)

        assertNull(d.activate(p, chronomancer).error)
        d.resolveAll(Choices(pick = listOf("Sol Ring")))

        assertEquals(listOf("Sol Ring"), d.zoneNames(p, Zone.GRAVEYARD))
        assertEquals(handBefore + 1, d.getHandSize(p))
        assertNotNull(d.findPermanent(p, "Chronomancer"))
        assertTrue(d.isTapped(chronomancer))
        assertFalse(ring in d.getPermanents(p))

        d.replaceState(d.state.updateEntity(chronomancer) { it.without<TappedComponent>() })
        d.giveColorlessMana(p, 1)
        assertNotNull("Chronomancer cannot sacrifice itself", d.activate(p, chronomancer).error)
    }

    @Test
    fun unearthReturnsChronomancerWithHasteAndExilesItAtEndStep() {
        val d = customScenario(Chronomancer)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Chronomancer")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNull(d.activate(p, card, index = 1).error)
        d.resolveAll()

        assertTrue(card in d.getPermanents(p))
        assertTrue(d.projected.hasKeyword(card, Keyword.HASTE))
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.END)
        d.resolveAll()
        assertFalse(card in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Chronomancer"))
    }
}
