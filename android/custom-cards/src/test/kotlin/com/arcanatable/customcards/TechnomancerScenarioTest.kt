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
 * Library cards: Bottle Gnomes {3}, Palladium Myr {3}, Juggernaut {4}, Steel Wall {1}, Coal Golem {5}
 * (artifact creatures); Grizzly Bears
 * (not an artifact). Technomancer itself is on the battlefield when its trigger resolves, so it is never
 * among the choices here.
 */
class TechnomancerScenarioTest {

    private fun game() = customScenario(
        Technomancer, "Bottle Gnomes", "Palladium Myr", "Juggernaut", "Grizzly Bears", "Steel Wall", "Coal Golem",
    )

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
    fun aSelectionTotallingSevenIsRefused() {
        val d = game()
        val p = d.player1
        val gnomes = d.putCardInGraveyard(p, "Bottle Gnomes")
        val juggernaut = d.putCardInGraveyard(p, "Juggernaut")

        val decision = d.castUntilChoice(p)
        assertEquals(setOf(gnomes, juggernaut), decision.options.toSet())
        assertEquals("only one of {3} and {4} fits under 6", 1, decision.maxSelections)

        assertNotNull("3 + 4 = 7 is over the cap", d.submitCardSelection(p, listOf(gnomes, juggernaut)).error)
        assertTrue("the choice is still pending", d.state.pendingDecision is SelectCardsDecision)
        d.select(p, listOf(juggernaut))
        assertEquals(1, d.onBattlefield(p, "Juggernaut"))
        assertEquals(0, d.onBattlefield(p, "Bottle Gnomes"))
        assertTrue(gnomes in d.getGraveyard(p))
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

    /**
     * Steel Wall {1} and two Coal Golems {5}: two cards "fit" (1 + 5), so a two-card pick is allowed by
     * count, but 5 + 5 = 10 is still over the cap. The engine checks the actual sum when the choice is
     * applied, keeping cards in the order picked while the running total stays at 6 or less
     * (LibraryAndZoneContinuationResumer, TotalManaValueAtMost), so only the first Golem returns.
     */
    private fun GameTestDriver.oneFiveFive(p: EntityId): Triple<EntityId, EntityId, EntityId> {
        val wall = putCardInGraveyard(p, "Steel Wall")
        val golem = putCardInGraveyard(p, "Coal Golem")
        val golem2 = putCardInGraveyard(p, "Coal Golem")
        val decision = castUntilChoice(p)
        assertEquals(setOf(wall, golem, golem2), decision.options.toSet())
        assertEquals("1 + 5 is the most cards that fit", 2, decision.maxSelections)
        return Triple(wall, golem, golem2)
    }

    private fun GameTestDriver.returnedManaValue(p: EntityId) =
        permanentsNamed(p, "Steel Wall").size * 1 + permanentsNamed(p, "Coal Golem").size * 5

    @Test
    fun fivePlusFiveIsNeverReturnedTogether() {
        val d = game()
        val p = d.player1
        val (wall, golem, golem2) = d.oneFiveFive(p)
        d.select(p, listOf(golem, golem2))
        assertEquals("only the first Golem picked returns", listOf(golem), d.permanentsNamed(p, "Coal Golem"))
        assertTrue(golem2 in d.getGraveyard(p) && wall in d.getGraveyard(p))
        assertTrue(d.returnedManaValue(p) <= 6)
    }

    @Test
    fun onePlusFiveIsAccepted() {
        val d = game()
        val p = d.player1
        val (wall, golem, golem2) = d.oneFiveFive(p)
        d.select(p, listOf(wall, golem))
        assertTrue(wall in d.getPermanents(p) && golem in d.getPermanents(p))
        assertTrue(golem2 in d.getGraveyard(p))
        assertEquals(6, d.returnedManaValue(p))
    }

    @Test
    fun oneAloneAndZeroAreAccepted() {
        val d = game()
        val p = d.player1
        val (wall, _, _) = d.oneFiveFive(p)
        d.select(p, listOf(wall))
        assertEquals(listOf(wall), d.permanentsNamed(p, "Steel Wall"))
        assertTrue(d.permanentsNamed(p, "Coal Golem").isEmpty())

        val d2 = game()
        val p2 = d2.player1
        d2.oneFiveFive(p2)
        d2.select(p2, emptyList())
        assertEquals(0, d2.returnedManaValue(p2))
        assertEquals(3, d2.zoneNames(p2, Zone.GRAVEYARD).count { it == "Steel Wall" || it == "Coal Golem" })
    }
}
