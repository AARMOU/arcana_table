package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekScarabSwarm
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CanoptekScarabSwarmIndependentCertificationTest {
    @Test
    fun costsFourGenericManaAndIsAOneOneFlyingArtifactCreature() {
        val d = customScenario(CanoptekScarabSwarm)
        val player = d.player1
        val card = d.putCardInHand(player, "Canoptek Scarab Swarm")
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))
        assertEquals(3, d.manaPool(player).colorless)

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        assertEquals(0, d.manaPool(player).colorless)
        d.resolveAll()

        val scarab = d.permanentsNamed(player, "Canoptek Scarab Swarm").single()
        assertTrue(d.projected.hasType(scarab, "ARTIFACT"))
        assertTrue(d.projected.isCreature(scarab))
        assertEquals(1, d.projected.getPower(scarab))
        assertEquals(1, d.projected.getToughness(scarab))
        assertTrue(d.projected.hasKeyword(scarab, Keyword.FLYING))
    }

    @Test
    fun enteringExilesTheChosenOpponentsGraveyardAndMakesTokensForArtifactsAndLands() {
        val d = customScenario(CanoptekScarabSwarm, "Sol Ring", "Plains", "Grizzly Bears", "Shock", "Forest")
        val player = d.player1
        val opponent = d.player2
        d.putCardInGraveyard(opponent, "Sol Ring")
        d.putCardInGraveyard(opponent, "Plains")
        d.putCardInGraveyard(opponent, "Grizzly Bears")
        d.putCardInGraveyard(opponent, "Shock")
        d.putCardInGraveyard(player, "Forest")
        val card = d.putCardInHand(player, "Canoptek Scarab Swarm")
        d.giveColorlessMana(player, 4)

        assertNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(opponent, Zone.EXILE).isEmpty())
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
        assertEquals(
            listOf("Grizzly Bears", "Plains", "Shock", "Sol Ring"),
            d.getGraveyardCardNames(opponent).sorted(),
        )
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertEquals(
            listOf("Grizzly Bears", "Plains", "Shock", "Sol Ring"),
            d.zoneNames(opponent, Zone.EXILE).sorted(),
        )
        assertTrue(d.getGraveyardCardNames(opponent).isEmpty())
        assertEquals(listOf("Forest"), d.getGraveyardCardNames(player))

        val tokens = d.getPermanents(player).filter {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        }
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertTrue(d.projected.hasType(token, "ARTIFACT"))
            assertTrue(d.projected.isCreature(token))
            assertTrue(d.projected.hasSubtype(token, "INSECT"))
            assertEquals(1, d.projected.getPower(token))
            assertEquals(1, d.projected.getToughness(token))
            assertTrue(d.projected.getColors(token).isEmpty())
            assertTrue(d.projected.hasKeyword(token, Keyword.FLYING))
        }
    }

    @Test
    fun canTargetItsControllersGraveyardButNonartifactNonlandCardsMakeNoTokens() {
        val d = customScenario(CanoptekScarabSwarm, "Grizzly Bears", "Shock", "Plains")
        val player = d.player1
        val opponent = d.player2
        d.putCardInGraveyard(player, "Grizzly Bears")
        d.putCardInGraveyard(player, "Shock")
        d.putCardInGraveyard(opponent, "Plains")
        val card = d.putCardInHand(player, "Canoptek Scarab Swarm")
        d.giveColorlessMana(player, 4)

        assertNull(d.castSpell(player, card).error)
        d.resolveAll(Choices(targets = listOf(player)))

        assertEquals(listOf("Grizzly Bears", "Shock"), d.zoneNames(player, Zone.EXILE).sorted())
        assertTrue(d.getGraveyardCardNames(player).isEmpty())
        assertEquals(listOf("Plains"), d.getGraveyardCardNames(opponent))
        assertTrue(d.getPermanents(player).none {
            d.state.getEntity(it)?.has<TokenComponent>() == true
        })
    }
}
