package com.arcanatable.customcards

// Independent Oracle certification for Malanthrope, queue #386 / Copilot implementation #396.
// Tests use the real pinned Argentum game driver and do not modify the card implementation.
// Oracle: Flying; ETB exiles target player's entire graveyard, then adds one +1/+1
// counter for each CREATURE CARD actually exiled this way (not each exiled card).
import com.arcanatable.customcards.cards.Malanthrope
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class MalanthropeIndependentCertificationTest {
    private fun GameTestDriver.castMalanthrope(targetPlayer: com.wingedsheep.sdk.model.EntityId): com.wingedsheep.sdk.model.EntityId {
        val card = putCardInHand(player1, "Malanthrope")
        giveMana(player1, Color.GREEN)
        giveMana(player1, Color.BLUE)
        giveColorlessMana(player1, 1)
        assertNull(castSpell(player1, card).error)
        resolveAll(Choices(targets = listOf(targetPlayer)))
        return card
    }

    @Test
    fun mixedGraveyardExilesAllCardsButCountsOnlyCreatures() {
        val d = customScenario(Malanthrope, "Grizzly Bears", "Giant Spider", "Forest", "Island")
        val opponent = d.player2
        d.putCardInGraveyard(opponent, "Grizzly Bears")
        d.putCardInGraveyard(opponent, "Giant Spider")
        d.putCardInGraveyard(opponent, "Forest")
        d.putCardInGraveyard(opponent, "Island")
        d.putCardInGraveyard(d.player1, "Grizzly Bears")

        val malanthrope = d.castMalanthrope(opponent)
        assertTrue(d.projected.hasKeyword(malanthrope, Keyword.FLYING))
        assertEquals("Two creatures among four exiled cards yield exactly two counters",
            4, d.projected.getPower(malanthrope))
        assertEquals(4, d.projected.getToughness(malanthrope))
        assertEquals(listOf("Forest", "Giant Spider", "Grizzly Bears", "Island"),
            d.zoneNames(opponent, Zone.EXILE).sorted())
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).isEmpty())
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(d.player1, Zone.GRAVEYARD))
    }

    @Test
    fun emptyTargetGraveyardCannotCountOtherPlayersCreatures() {
        val d = customScenario(Malanthrope, "Grizzly Bears")
        d.putCardInGraveyard(d.player1, "Grizzly Bears")
        val malanthrope = d.castMalanthrope(d.player2)

        assertEquals("No creature card was exiled by this trigger", 2, d.projected.getPower(malanthrope))
        assertEquals(2, d.projected.getToughness(malanthrope))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).isEmpty())
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(d.player1, Zone.GRAVEYARD))
    }

    @Test
    fun targetingSelfExilesOnlySelfGraveyardNotOpponents() {
        val d = customScenario(Malanthrope, "Grizzly Bears", "Forest")
        d.putCardInGraveyard(d.player1, "Grizzly Bears")
        d.putCardInGraveyard(d.player1, "Forest")
        d.putCardInGraveyard(d.player2, "Grizzly Bears")
        d.putCardInGraveyard(d.player2, "Grizzly Bears")

        val malanthrope = d.castMalanthrope(d.player1)
        assertEquals("Only one creature card was exiled from the chosen graveyard",
            3, d.projected.getPower(malanthrope))
        assertEquals(listOf("Forest", "Grizzly Bears"), d.zoneNames(d.player1, Zone.EXILE).sorted())
        assertEquals(listOf("Grizzly Bears", "Grizzly Bears"),
            d.zoneNames(d.player2, Zone.GRAVEYARD).sorted())
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).isEmpty())
    }

    @Test
    fun coloredManaRequirementsCannotBePaidWithExtraGenericMana() {
        val d = customScenario(Malanthrope)
        val card = d.putCardInHand(d.player1, "Malanthrope")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 2)
        assertNotNull("Missing blue mana must reject the {1}{G}{U} cost",
            d.castSpell(d.player1, card).error)
        d.giveMana(d.player1, Color.BLUE)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertTrue(d.getPermanents(d.player1).contains(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(2, d.projected.getPower(card))
        assertEquals(2, d.projected.getToughness(card))
    }
}
