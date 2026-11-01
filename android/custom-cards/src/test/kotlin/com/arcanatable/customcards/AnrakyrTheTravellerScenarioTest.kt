package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AnrakyrTheTraveller
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class AnrakyrTheTravellerScenarioTest {
    @Test
    fun castsArtifactSpellFromHandByPayingLifeEqualToItsManaValue() {
        val d = customScenario(AnrakyrTheTraveller, "Myr Battlesphere")
        val player = d.player1
        d.putCardInHand(player, "Myr Battlesphere")

        d.declareAnrakyrAttack()
        d.resolveAll(Choices(pick = listOf("Myr Battlesphere")))

        assertEquals(13, d.getLifeTotal(player))
        assertEquals(1, d.permanentsNamed(player, "Myr Battlesphere").size)
        assertFalse(d.zoneNames(player, Zone.HAND).contains("Myr Battlesphere"))
    }

    @Test
    fun castsArtifactSpellFromGraveyardByPayingLifeEqualToItsManaValue() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val player = d.player1
        d.putCardInGraveyard(player, "Sol Ring")

        d.declareAnrakyrAttack()
        d.resolveAll(Choices(pick = listOf("Sol Ring")))

        assertEquals(19, d.getLifeTotal(player))
        assertEquals(1, d.permanentsNamed(player, "Sol Ring").size)
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Sol Ring"))
    }

    @Test
    fun mayDeclineToCastAnArtifactSpell() {
        val d = customScenario(AnrakyrTheTraveller, "Sol Ring")
        val player = d.player1
        d.putCardInHand(player, "Sol Ring")

        d.declareAnrakyrAttack()
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(player, "Sol Ring").isEmpty())
    }

    @Test
    fun doesNotOfferNonartifactCardsArtifactLandsOrOpponentsCards() {
        val d = customScenario(AnrakyrTheTraveller, "Grizzly Bears", "Darksteel Citadel", "Sol Ring")
        val player = d.player1
        d.putCardInHand(player, "Grizzly Bears")
        d.putCardInHand(player, "Darksteel Citadel")
        d.putCardInGraveyard(d.player2, "Sol Ring")

        d.declareAnrakyrAttack()

        val offeredCards = (d.state.pendingDecision as? SelectCardsDecision)
            ?.options.orEmpty().mapNotNull(d::getCardName)
        assertTrue("Nonartifact cards and artifact lands are not spell choices: $offeredCards",
            offeredCards.isEmpty())
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.zoneNames(player, Zone.HAND).containsAll(listOf("Grizzly Bears", "Darksteel Citadel")))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
    }

    @Test
    fun cannotCastAnArtifactSpellWhenItsLifeCostExceedsYourLifeTotal() {
        val d = customScenario(AnrakyrTheTraveller, "Myr Battlesphere")
        val player = d.player1
        d.setLifeTotal(player, 6)
        d.putCardInHand(player, "Myr Battlesphere")

        d.declareAnrakyrAttack()
        d.resolveAll(Choices(pick = listOf("Myr Battlesphere")))

        assertEquals(6, d.getLifeTotal(player))
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Myr Battlesphere"))
        assertTrue(d.permanentsNamed(player, "Myr Battlesphere").isEmpty())
    }

    private fun GameTestDriver.declareAnrakyrAttack() {
        val player = player1
        val anrakyr = putCreatureOnBattlefield(player, "Anrakyr the Traveller")
        removeSummoningSickness(anrakyr)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player, listOf(anrakyr), player2).error)
        bothPass()
    }
}
