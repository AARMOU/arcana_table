package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekScarabSwarm
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Canoptek Scarab Swarm (40K #150): Flying; Feeder Mandibles — when it enters, exile target player's
 * graveyard, then one 1/1 colorless flying Insect artifact creature token per artifact or land card
 * exiled this way. Official ruling (2022-10-07): "If an exiled card is both an artifact card and a land
 * card, you will create one token, not two."
 */
class CanoptekScarabSwarmScenarioTest {

    private fun game() = customScenario(CanoptekScarabSwarm, "Darksteel Citadel", "Sol Ring", "Grizzly Bears", "Ornithopter")

    /** Casts the Swarm and resolves it, pointing Feeder Mandibles at [target]. */
    private fun GameTestDriver.castSwarm(p: EntityId, target: EntityId) {
        val swarm = putCardInHand(p, "Canoptek Scarab Swarm")
        giveColorlessMana(p, 4)
        assertNull(castSpell(p, swarm).error)
        repeat(10) { if (state.pendingDecision == null && state.stack.isNotEmpty()) bothPass() }
        val decision = state.pendingDecision as ChooseTargetsDecision
        assertEquals("either player is a legal target", setOf(player1, player2), decision.legalTargets[0].orEmpty().toSet())
        resolveAll(Choices(targets = listOf(target)))
        assertNotNull(findPermanent(p, "Canoptek Scarab Swarm"))
    }

    private fun GameTestDriver.insects(p: EntityId) = permanentsNamed(p, "Insect")

    @Test
    fun exilesTheOpponentsGraveyardAndCountsArtifactOrLandCardsOnce() {
        val d = game()
        val p = d.player1
        val opp = d.player2
        for (name in listOf("Darksteel Citadel", "Sol Ring", "Plains", "Grizzly Bears")) d.putCardInGraveyard(opp, name)
        d.putCardInGraveyard(p, "Sol Ring")

        d.castSwarm(p, opp)

        assertTrue(d.zoneNames(opp, Zone.GRAVEYARD).isEmpty())
        assertEquals(listOf("Darksteel Citadel", "Grizzly Bears", "Plains", "Sol Ring"), d.zoneNames(opp, Zone.EXILE).sorted())
        assertEquals("your own graveyard is untouched", listOf("Sol Ring"), d.zoneNames(p, Zone.GRAVEYARD))
        val tokens = d.insects(p)
        assertEquals("artifact land once + artifact + land; the creature card doesn't count", 3, tokens.size)
        for (t in tokens) {
            assertEquals(1, d.projected.getPower(t))
            assertEquals(1, d.projected.getToughness(t))
            assertTrue("colorless", d.projected.getColors(t).isEmpty())
            assertTrue(d.projected.hasKeyword(t, Keyword.FLYING))
            assertTrue(d.projected.isCreature(t))
            assertTrue(d.projected.hasType(t, "ARTIFACT"))
            assertTrue(d.projected.hasSubtype(t, "Insect"))
        }
        val swarm = d.findPermanent(p, "Canoptek Scarab Swarm")!!
        assertTrue(d.projected.hasKeyword(swarm, Keyword.FLYING))
        assertEquals(1, d.projected.getPower(swarm))
    }

    @Test
    fun canTargetYourOwnGraveyard() {
        val d = game()
        val p = d.player1
        val opp = d.player2
        d.putCardInGraveyard(p, "Darksteel Citadel")
        d.putCardInGraveyard(p, "Ornithopter") // artifact creature card: an artifact card, counts
        d.putCardInGraveyard(p, "Grizzly Bears")
        d.putCardInGraveyard(opp, "Sol Ring")

        d.castSwarm(p, p)

        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).isEmpty())
        assertEquals(listOf("Darksteel Citadel", "Grizzly Bears", "Ornithopter"), d.zoneNames(p, Zone.EXILE).sorted())
        assertEquals("the opponent's graveyard is untouched", listOf("Sol Ring"), d.zoneNames(opp, Zone.GRAVEYARD))
        assertEquals(2, d.insects(p).size)
    }

    @Test
    fun onlyCreatureCardsMakeNoTokens() {
        val d = game()
        val p = d.player1
        d.putCardInGraveyard(d.player2, "Grizzly Bears")
        d.castSwarm(p, d.player2)
        assertEquals(listOf("Grizzly Bears"), d.zoneNames(d.player2, Zone.EXILE))
        assertTrue(d.insects(p).isEmpty())
    }

    @Test
    fun anEmptyGraveyardMakesNoTokens() {
        val d = game()
        val p = d.player1
        d.castSwarm(p, d.player2)
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).isEmpty())
        assertTrue(d.insects(p).isEmpty())
    }
}
