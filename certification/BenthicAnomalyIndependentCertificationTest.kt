package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BenthicAnomaly
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class BenthicAnomalyIndependentCertificationTest {
    private val name = "Benthic Anomaly"

    @Test
    fun castingForSixGenericAndBlueEntersAsAColorlessSevenEight() {
        val d = customScenario(BenthicAnomaly, "Grizzly Bears")
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveColorlessMana(p, 6)
        d.giveMana(p, Color.BLUE)

        assertNull(d.castSpell(p, card).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).blue)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertTrue("Devoid makes Benthic Anomaly colorless", d.projected.getColors(card).isEmpty())
        assertEquals(7, d.projected.getPower(card))
        assertEquals(8, d.projected.getToughness(card))
    }

    @Test
    fun sixColorlessManaCannotReplaceBlueAndBlueWithFiveGenericIsInsufficient() {
        val withoutBlue = customScenario(BenthicAnomaly)
        val p = withoutBlue.player1
        val card = withoutBlue.putCardInHand(p, name)
        withoutBlue.giveColorlessMana(p, 6)

        assertNotNull(withoutBlue.castSpell(p, card).error)
        assertTrue(withoutBlue.getHand(p).contains(card))
        assertEquals(6, withoutBlue.manaPool(p).colorless)

        val shortGeneric = customScenario(BenthicAnomaly)
        val shortCard = shortGeneric.putCardInHand(shortGeneric.player1, name)
        shortGeneric.giveColorlessMana(shortGeneric.player1, 5)
        shortGeneric.giveMana(shortGeneric.player1, Color.BLUE)

        assertNotNull(shortGeneric.castSpell(shortGeneric.player1, shortCard).error)
        assertTrue(shortGeneric.getHand(shortGeneric.player1).contains(shortCard))
        assertEquals(5, shortGeneric.manaPool(shortGeneric.player1).colorless)
        assertEquals(1, shortGeneric.manaPool(shortGeneric.player1).blue)
    }

    @Test
    fun castTriggerUsesEachOpponentsCreatureAndMakesOneColorlessEldraziCopyWithTheirTotalStats() {
        val d = customScenario(BenthicAnomaly, "Grizzly Bears", "Serra Angel", "Colossal Dreadmaw")
        val players = d.initMultiplayer(decks = List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val p = players[0]
        val bears = d.putCreatureOnBattlefield(players[1], "Grizzly Bears")
        val angel = d.putCreatureOnBattlefield(players[2], "Serra Angel")
        d.putCreatureOnBattlefield(p, "Colossal Dreadmaw")
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 6)
        d.giveMana(p, Color.BLUE)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        val tokens = d.getPermanents(p).filter { d.state.getEntity(it)?.has<TokenComponent>() == true }
        assertEquals(1, tokens.size)
        val token = tokens.single()
        assertEquals("Grizzly Bears", d.getCardName(token))
        assertEquals(6, d.projected.getPower(token))
        assertEquals(6, d.projected.getToughness(token))
        assertTrue(d.projected.getColors(token).isEmpty())
        assertTrue(d.projected.hasType(token, "CREATURE"))
        assertTrue(d.projected.hasSubtype(token, "ELDRAZI"))
        assertFalse(d.projected.hasSubtype(token, "BEAR"))
        assertTrue(d.getPermanents(players[1]).contains(bears))
        assertTrue(d.getPermanents(players[2]).contains(angel))
    }

    @Test
    fun puttingBenthicAnomalyOntoTheBattlefieldDoesNotTriggerItsCastAbility() {
        val d = customScenario(BenthicAnomaly, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, name)
        d.resolveAll()

        assertTrue(d.getPermanents(p).none { d.state.getEntity(it)?.has<TokenComponent>() == true })
    }
}
