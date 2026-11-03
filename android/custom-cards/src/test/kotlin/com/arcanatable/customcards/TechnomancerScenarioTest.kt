package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Technomancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Technomancer (40K #61): when it enters, mill three cards, then return any number of artifact creature
 * cards with total mana value 6 or less from your graveyard to the battlefield. Rulings (2022-10-07) quoted
 * in [Technomancer]: the returned cards don't need to be ones just milled.
 *
 * Library cards: Bottle Gnomes {3}, Palladium Myr {3}, Juggernaut {4} (artifact creatures); Grizzly Bears
 * (not an artifact). Technomancer itself is on the battlefield when its trigger resolves, so it is never
 * among the choices here.
 */
class TechnomancerScenarioTest {

    private fun game() = customScenario(Technomancer, "Bottle Gnomes", "Palladium Myr", "Juggernaut", "Grizzly Bears")

    /** Casts Technomancer and resolves until its enters trigger asks which cards to return. */
    private fun GameTestDriver.castUntilChoice(p: EntityId): SelectCardsDecision {
        val card = putCardInHand(p, "Technomancer")
        giveMana(p, Color.BLACK, 2)
        giveColorlessMana(p, 5)
        assertNull(castSpell(p, card).error)
        var guard = 0
        while (state.pendingDecision == null && state.stack.isNotEmpty() && guard++ < 20) bothPass()
        val decision = state.pendingDecision
        assertTrue("expected the return choice, got $decision", decision is SelectCardsDecision)
        return decision as SelectCardsDecision
    }

    private fun GameTestDriver.select(p: EntityId, cards: List<EntityId>) {
        assertNull(submitCardSelection(p, cards).error)
        resolveAll()
    }

    private fun GameTestDriver.onBattlefield(p: EntityId, name: String) = permanentsNamed(p, name).size

    @Test
    fun returnsAMilledCardAndOneAlreadyThereWithTotalSix() {
        val d = game()
        val p = d.player1
        val gnomes = d.putCardInGraveyard(p, "Bottle Gnomes")
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        d.putCardOnTopOfLibrary(p, "Plains")
        val myr = d.putCardOnTopOfLibrary(p, "Palladium Myr")
        val library = d.state.getZone(p, Zone.LIBRARY).size

        val decision = d.castUntilChoice(p)
        assertEquals("milled three", library - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertTrue("just milled", myr in d.getGraveyard(p))
        assertEquals("only artifact creature cards are offered", setOf(gnomes, myr), decision.options.toSet())
        assertEquals("any number includes zero", 0, decision.minSelections)

        d.select(p, listOf(gnomes, myr))
        assertEquals(1, d.onBattlefield(p, "Bottle Gnomes"))
        assertEquals(1, d.onBattlefield(p, "Palladium Myr"))
        assertEquals(1, d.onBattlefield(p, "Technomancer"))
        assertTrue("non-artifact creature stays", d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }

    @Test
    fun aSelectionTotallingSevenIsCutBackToTheCap() {
        val d = game()
        val p = d.player1
        val gnomes = d.putCardInGraveyard(p, "Bottle Gnomes")
        val juggernaut = d.putCardInGraveyard(p, "Juggernaut")

        val decision = d.castUntilChoice(p)
        assertEquals(setOf(gnomes, juggernaut), decision.options.toSet())

        d.select(p, listOf(gnomes, juggernaut))
        assertEquals("3 fits", 1, d.onBattlefield(p, "Bottle Gnomes"))
        assertEquals("3 + 4 = 7 is over the cap, so Juggernaut stays", 0, d.onBattlefield(p, "Juggernaut"))
        assertTrue(juggernaut in d.getGraveyard(p))
    }

    @Test
    fun choosingNothingReturnsNothing() {
        val d = game()
        val p = d.player1
        val gnomes = d.putCardInGraveyard(p, "Bottle Gnomes")

        d.castUntilChoice(p)
        d.select(p, emptyList())
        assertEquals(0, d.onBattlefield(p, "Bottle Gnomes"))
        assertTrue(gnomes in d.getGraveyard(p))
        assertEquals(1, d.onBattlefield(p, "Technomancer"))
    }

    @Test
    fun nonArtifactCreatureCardsAreNeverReturned() {
        val d = game()
        val p = d.player1
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val library = d.state.getZone(p, Zone.LIBRARY).size

        val card = d.putCardInHand(p, "Technomancer")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 5)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertEquals("still milled three", library - 3, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(0, d.onBattlefield(p, "Grizzly Bears"))
        assertEquals(2, d.zoneNames(p, Zone.GRAVEYARD).count { it == "Grizzly Bears" })
    }
}
