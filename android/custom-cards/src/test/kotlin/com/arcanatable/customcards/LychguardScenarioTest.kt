package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Lychguard
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

/**
 * Lychguard (40K #39): Guardian Protocols — {3}{B}, Sacrifice this creature: Return all legendary creature
 * cards from your graveyard to your hand. No official rulings are published; the sacrifice is part of the
 * cost (CR 602.2b, 601.2h), so it happens before the ability resolves.
 */
class LychguardScenarioTest {

    private fun game() = customScenario(
        Lychguard, "Isamaru, Hound of Konda", "Captain Sisay", "Grizzly Bears", "The One Ring", "Kenrith, the Returned King",
    )

    @Test
    fun returnsAllLegendaryCreatureCardsFromYourGraveyardOnly() {
        val d = game()
        val p = d.player1
        val lychguard = d.putCreatureOnBattlefield(p, "Lychguard")
        val isamaru = d.putCardInGraveyard(p, "Isamaru, Hound of Konda")
        val sisay = d.putCardInGraveyard(p, "Captain Sisay")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")
        val ring = d.putCardInGraveyard(p, "The One Ring")
        val opponentsLegend = d.putCardInGraveyard(d.player2, "Kenrith, the Returned King")

        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)
        assertNull(d.activate(p, lychguard).error)
        assertTrue("sacrificed as a cost, before resolution", lychguard in d.state.getGraveyard(p))
        assertFalse(lychguard in d.state.getBattlefield())
        d.resolveAll()

        assertTrue(isamaru in d.getHand(p))
        assertTrue(sisay in d.getHand(p))
        assertTrue("nonlegendary creature card stays", bears in d.state.getGraveyard(p))
        assertTrue("legendary noncreature card stays", ring in d.state.getGraveyard(p))
        assertTrue("Lychguard itself (not legendary) stays", lychguard in d.state.getGraveyard(p))
        assertTrue("opponent's graveyard untouched", opponentsLegend in d.state.getGraveyard(d.player2))
        assertFalse(opponentsLegend in d.getHand(p))
        assertEquals(listOf("Isamaru, Hound of Konda", "Captain Sisay").sorted(), d.zoneNames(p, Zone.HAND).filter { it != "Plains" }.sorted())
    }

    @Test
    fun cannotActivateWithoutThreeAndBlack() {
        val d = game()
        val p = d.player1
        val lychguard = d.putCreatureOnBattlefield(p, "Lychguard")
        val isamaru = d.putCardInGraveyard(p, "Isamaru, Hound of Konda")

        assertNotNull("no mana", d.activate(p, lychguard).error)
        d.giveColorlessMana(p, 4)
        assertNotNull("four generic but no {B}", d.activate(p, lychguard).error)
        assertTrue(lychguard in d.state.getBattlefield())
        assertTrue(isamaru in d.state.getGraveyard(p))

        d.giveMana(p, Color.BLACK)
        assertNull(d.activate(p, lychguard).error)
        d.resolveAll()
        assertTrue(isamaru in d.getHand(p))
    }

    @Test
    fun withNoLegendaryCreatureCardItStillResolves() {
        val d = game()
        val p = d.player1
        val lychguard = d.putCreatureOnBattlefield(p, "Lychguard")
        val bears = d.putCardInGraveyard(p, "Grizzly Bears")
        val handBefore = d.getHand(p).toSet()
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)
        assertNull(d.activate(p, lychguard).error)
        d.resolveAll()
        assertEquals(handBefore, d.getHand(p).toSet())
        assertTrue(bears in d.state.getGraveyard(p))
        assertTrue(lychguard in d.state.getGraveyard(p))
    }
}
