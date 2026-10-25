package com.arcanatable.customcards

import com.arcanatable.customcards.cards.OblivionSower
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import org.junit.Assert.*
import org.junit.Test

class OblivionSowerScenarioTest {
    @Test
    fun castTriggerExilesFourAndLetsYouTakeAnyOwnedLandsFromExile() {
        val d = customScenario(OblivionSower, "Forest", "Mountain", "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        listOf("Forest", "Mountain", "Sol Ring", "Grizzly Bears").forEach {
            d.putCardOnTopOfLibrary(opponent, it)
        }
        val sower = d.putCardInHand(p, "Oblivion Sower")
        d.giveColorlessMana(p, 6)

        assertNull(d.castSpell(p, sower).error)
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest", "Mountain")))

        assertEquals(2, d.permanentsNamed(p, "Forest").size)
        assertEquals(1, d.permanentsNamed(p, "Mountain").size)
        assertNotNull(d.findPermanent(p, "Oblivion Sower"))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Sol Ring", "Grizzly Bears")))
    }
    @Test
    fun mayChooseZeroExiledLandsWithoutReversingTheExile() {
        val d = customScenario(OblivionSower, "Forest", "Mountain", "Sol Ring", "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        listOf("Forest", "Mountain", "Sol Ring", "Grizzly Bears").forEach {
            d.putCardOnTopOfLibrary(opponent, it)
        }
        val sower = d.putCardInHand(caster, "Oblivion Sower")
        d.giveColorlessMana(caster, 6)

        assertNull(d.castSpell(caster, sower).error)
        // "May put any number" includes zero. An unmatched choice declines all optional lands.
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Decline all lands")))

        assertEquals(0, d.permanentsNamed(caster, "Forest").size)
        assertEquals(0, d.permanentsNamed(caster, "Mountain").size)
        assertEquals(2, d.zoneNames(opponent, Zone.EXILE).count { it == "Forest" })
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(
            listOf("Mountain", "Sol Ring", "Grizzly Bears")
        ))
        assertNotNull(d.findPermanent(caster, "Oblivion Sower"))
    }

    @Test
    fun preexistingExiledLandsMayBeChosenEvenWhenTopFourAreNonlands() {
        val d = customScenario(OblivionSower, "Forest", "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        repeat(4) { d.putCardOnTopOfLibrary(opponent, "Grizzly Bears") }
        val sower = d.putCardInHand(caster, "Oblivion Sower")
        d.giveColorlessMana(caster, 6)

        assertNull(d.castSpell(caster, sower).error)
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest")))

        assertEquals(1, d.permanentsNamed(caster, "Forest").size)
        assertEquals(4, d.zoneNames(opponent, Zone.EXILE).count { it == "Grizzly Bears" })
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Forest"))
        assertNotNull(d.findPermanent(caster, "Oblivion Sower"))
    }


    @Test fun ownLandsAndNonlandsAreNotChoicesButOpponentOwnedLandsInAnotherExileBucketAre() {
        val d = customScenario(OblivionSower, "Forest", "Mountain", "Grizzly Bears")
        val own = d.putCardInExile(d.player1, "Forest")
        val ownedByOpponent = d.putCardInExile(d.player2, "Mountain")
        // Exile is a shared zone. Test ownership independently of this engine's zone buckets.
        d.replaceState(d.state.removeFromZone(ZoneKey(d.player2, Zone.EXILE), ownedByOpponent)
            .addToZone(ZoneKey(d.player1, Zone.EXILE), ownedByOpponent))
        repeat(4) { d.putCardOnTopOfLibrary(d.player2, "Grizzly Bears") }
        d.giveColorlessMana(d.player1, 6)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Oblivion Sower")).error)
        var guard = 0
        while (d.pendingDecision !is SelectCardsDecision &&
            (d.pendingDecision != null || d.state.stack.isNotEmpty()) && guard++ < 30) {
            if (d.pendingDecision != null) d.decide(Choices(targets = listOf(d.player2))) else d.bothPass()
        }
        val choice = d.pendingDecision as? SelectCardsDecision ?: error("No optional land choice")
        assertEquals("Actual decision: $choice", d.player1, choice.playerId)
        assertEquals(0, choice.minSelections)
        assertEquals(listOf(ownedByOpponent), choice.options)
        assertFalse(own in choice.options)
        assertNull(d.submitCardSelection(d.player1, listOf(ownedByOpponent)).error)
        d.resolveAll()
        assertTrue(own in d.state.getZone(d.player1, Zone.EXILE))
        assertEquals(4, d.zoneNames(d.player2, Zone.EXILE).count { it == "Grizzly Bears" })
        assertEquals(d.player1, d.projected.getController(ownedByOpponent))
        assertEquals(d.player2, d.state.getEntity(ownedByOpponent)!!.get<OwnerComponent>()!!.playerId)
    }

    @Test fun enteringWithoutCastingDoesNotExileOrStealAnything() {
        val d = customScenario(OblivionSower, "Forest")
        val land = d.putCardInExile(d.player2, "Forest")
        val library = d.state.getZone(d.player2, Zone.LIBRARY).toList()
        d.putCreatureOnBattlefield(d.player1, "Oblivion Sower")
        d.resolveAll()
        assertEquals(library, d.state.getZone(d.player2, Zone.LIBRARY))
        assertTrue(land in d.state.getZone(d.player2, Zone.EXILE))
        assertTrue(d.permanentsNamed(d.player1, "Forest").isEmpty())
    }

}
