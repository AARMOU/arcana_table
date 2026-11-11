package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Plasmancer
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.LibrarySearchedEvent
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class PlasmancerIndependentCertificationTest {
    private val name = "Plasmancer"

    private fun GameTestDriver.castPlasmancer(player: EntityId): EntityId {
        val card = putCardInHand(player, name)
        giveColorlessMana(player, 2)
        giveMana(player, Color.BLACK, 2)
        assertNull(castSpell(player, card).error)
        return card
    }

    @Test
    fun castingCostsTwoGenericAndTwoBlackAndEntersAsAThreeThreeFlyingArtifactCreature() {
        val d = customScenario(Plasmancer)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK, 2)
        val searchesBefore = d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p }
        val shufflesBefore = d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p }

        assertNull(d.castSpell(p, card).error)
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(searchesBefore, d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p })

        d.resolveAll()

        assertEquals(listOf(card), d.permanentsNamed(p, name))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertTrue(d.projected.isCreature(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).black)
        assertEquals(searchesBefore + 1, d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p })
        assertEquals(shufflesBefore + 1, d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p })
    }

    @Test
    fun fourGenericManaCannotReplaceTheTwoRequiredBlackMana() {
        val d = customScenario(Plasmancer)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 4)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(4, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).black)
    }

    @Test
    fun oneGenericManaAndTwoBlackManaCannotPayTheTwoGenericCost() {
        val d = customScenario(Plasmancer)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK, 2)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(2, d.manaPool(p).black)
    }

    @Test
    fun flyingPlasmancerCannotBeBlockedByAGroundCreatureButCanBeBlockedByAFlyer() {
        val d = customScenario(Plasmancer, "Grizzly Bears", "Serra Angel")
        val p = d.player1
        val opponent = d.player2
        val plasmancer = d.putCreatureOnBattlefield(p, name)
        val groundBlocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val flyingBlocker = d.putCreatureOnBattlefield(opponent, "Serra Angel")
        d.removeSummoningSickness(plasmancer)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(plasmancer, Keyword.FLYING))
        assertNull(d.declareAttackers(p, listOf(plasmancer), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, mapOf(groundBlocker to listOf(plasmancer))).error)
        assertNull(d.declareBlockers(opponent, mapOf(flyingBlocker to listOf(plasmancer))).error)
    }

    @Test
    fun enteringRevealsAndPutsABasicSwampIntoHandThenShuffles() {
        val d = customScenario(Plasmancer, "Swamp", "Forest", "Badlands")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Swamp")
        d.putCardOnTopOfLibrary(p, "Badlands")
        d.putCardOnTopOfLibrary(p, "Forest")
        val libraryBefore = d.zoneNames(p, Zone.LIBRARY)
        val searchesBefore = d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p }
        val shufflesBefore = d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p }
        val card = d.castPlasmancer(p)

        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Swamp"))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertFalse(d.events.any { it is CardsRevealedEvent && it.revealingPlayerId == p })
        assertEquals(searchesBefore, d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p })

        d.resolveAll(Choices(pick = listOf("Swamp")))

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Swamp"))
        val libraryAfter = d.zoneNames(p, Zone.LIBRARY)
        assertFalse(libraryAfter.contains("Swamp"))
        assertTrue(libraryAfter.contains("Badlands"))
        assertTrue(libraryAfter.contains("Forest"))
        assertEquals(libraryBefore.size - 1, libraryAfter.size)
        val reveal = d.events.filterIsInstance<CardsRevealedEvent>().single { it.revealingPlayerId == p }
        assertEquals(listOf("Swamp"), reveal.cardNames)
        assertEquals(searchesBefore + 1, d.events.filterIsInstance<LibrarySearchedEvent>().count { it.playerId == p })
        assertEquals(shufflesBefore + 1, d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p })
        val revealIndex = d.events.indexOf(reveal)
        val shuffleIndex = d.events.indexOfLast { it is LibraryShuffledEvent && it.playerId == p }
        assertTrue(revealIndex < shuffleIndex)
    }

    @Test
    fun aNonbasicSwampInYourLibraryCannotBeFound() {
        val d = customScenario(Plasmancer, "Badlands")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Badlands")
        val shufflesBefore = d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p }
        d.castPlasmancer(p)

        d.resolveAll(Choices(pick = listOf("Badlands")))

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Badlands"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Badlands"))
        assertFalse(d.events.any { it is CardsRevealedEvent && it.revealingPlayerId == p })
        assertEquals(shufflesBefore + 1, d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p })
    }

    @Test
    fun anOpponentsBasicSwampCannotBeFound() {
        val d = customScenario(Plasmancer)
        val p = d.player1
        val opponent = d.player2
        d.putCardOnTopOfLibrary(opponent, "Swamp")
        val playerShufflesBefore = d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p }
        val opponentShufflesBefore = d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == opponent }
        d.castPlasmancer(p)

        d.resolveAll(Choices(pick = listOf("Swamp")))

        assertFalse(d.zoneNames(p, Zone.HAND).contains("Swamp"))
        assertTrue(d.zoneNames(opponent, Zone.LIBRARY).contains("Swamp"))
        assertFalse(d.events.any { it is CardsRevealedEvent })
        assertEquals(playerShufflesBefore + 1, d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == p })
        assertEquals(opponentShufflesBefore, d.events.filterIsInstance<LibraryShuffledEvent>().count { it.playerId == opponent })
    }
}
